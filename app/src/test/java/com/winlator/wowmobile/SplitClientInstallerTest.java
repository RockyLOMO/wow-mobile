package com.winlator.wowmobile;

import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.zip.*;

/** Exercise actual HTTP resumes at and within split boundaries, and stream the resulting ZIP. */
public final class SplitClientInstallerTest {
    public static void main(String[] args) throws Exception {
        Path root=Files.createTempDirectory(Paths.get(args[0]),"split-test-");
        byte[] payload=new byte[3*1024*1024]; new Random(335).nextBytes(payload);
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try (ZipOutputStream zip=new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("client/Wow.exe")); zip.write(payload); zip.closeEntry();
        }
        byte[] archive=bytes.toByteArray(); byte[][] parts=new byte[6][];
        long[] sizes=new long[6]; String[] hashes=new String[6];
        for (int i=0;i<6;i++) {
            int start=i*archive.length/6,end=(i+1)*archive.length/6;
            parts[i]=Arrays.copyOfRange(archive,start,end); sizes[i]=parts[i].length; hashes[i]=sha(parts[i]);
        }
        AtomicLong requestedRange=new AtomicLong(-1); AtomicInteger requestedPart=new AtomicInteger(-1);
        AtomicBoolean ignoreRange=new AtomicBoolean(),corrupt=new AtomicBoolean(),badRange=new AtomicBoolean();
        AtomicInteger missing=new AtomicInteger(-1);
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/client.zip",exchange -> {
            String path=exchange.getRequestURI().getPath();
            int i;
            try { i=Integer.parseInt(path.substring(path.lastIndexOf('.')+1))-1; }
            catch (NumberFormatException e) { exchange.sendResponseHeaders(404,-1); exchange.close(); return; }
            if (i<0 || i>=6 || missing.get()==i) { exchange.sendResponseHeaders(404,-1); exchange.close(); return; }
            byte[] data=parts[i].clone(); if (corrupt.get() && i==2) data[100]^=1;
            exchange.getResponseHeaders().set("ETag","\"split-"+i+"\"");
            if (exchange.getRequestMethod().equals("HEAD")) {
                exchange.getResponseHeaders().set("Content-Length",""+data.length);
                exchange.sendResponseHeaders(200,-1); exchange.close(); return;
            }
            String range=exchange.getRequestHeaders().getFirst("Range");
            int offset=range==null || ignoreRange.get() ? 0:Integer.parseInt(range.substring(6,range.length()-1));
            requestedRange.set(offset); requestedPart.set(i);
            if (offset>0) exchange.getResponseHeaders().set("Content-Range","bytes "+(badRange.get()?offset+1:offset)+"-"+(data.length-1)+"/"+data.length);
            exchange.sendResponseHeaders(offset>0?206:200,data.length-offset);
            try (OutputStream out=exchange.getResponseBody()) { out.write(data,offset,data.length-offset); }
            catch (IOException ignored) {} finally { exchange.close(); }
        });
        server.start(); String url="http://127.0.0.1:"+server.getAddress().getPort()+"/client.zip";
        AtomicBoolean pause=new AtomicBoolean();
        ClientInstaller.Progress quiet=(p,d,t,x)->{};
        File cache=root.resolve("cache").toFile();
        try {
            try {
                ClientInstaller.downloadParts(url,sizes,hashes,cache,pause,(p,d,t,x)->{
                    if (p.equals("download") && d>sizes[0]+sizes[1] && d<sizes[0]+sizes[1]+sizes[2]) pause.set(true);
                }); throw new AssertionError("pause ignored");
            } catch (ClientInstaller.Paused expected) {}
            long saved=new File(cache,"client.zip.part").length();
            require(saved>sizes[0]+sizes[1] && saved<sizes[0]+sizes[1]+sizes[2],"paused inside third part");
            pause.set(false);
            try {
                ClientInstaller.downloadParts(url,sizes,hashes,cache,pause,(p,d,t,x)->{
                    if (p.equals("download") && x.equals("正在下载分片 3/6")) {
                        require(requestedPart.get()==2 && requestedRange.get()==saved-sizes[0]-sizes[1],"exact part-local range");
                        pause.set(true);
                    }
                }); throw new AssertionError("pause ignored");
            } catch (ClientInstaller.Paused expected) {}
            require(new File(cache,"client.zip.part").length()==saved,"resume paused without changing prefix");
            pause.set(false); badRange.set(true);
            expectFailure(()->ClientInstaller.downloadParts(url,sizes,hashes,cache,pause,quiet));
            require(new File(cache,"client.zip.part").length()==saved,"bad Content-Range preserves cache");
            badRange.set(false); ignoreRange.set(true);
            File completed=ClientInstaller.downloadParts(url,sizes,hashes,cache,pause,quiet);
            require(Arrays.equals(archive,Files.readAllBytes(completed.toPath())),"200 restarts current part and preserves prior parts");
            ignoreRange.set(false);
            File stage=root.resolve("stage").toFile(); stage.mkdirs();
            ClientInstaller.extract(completed,stage,pause,quiet);
            require(Arrays.equals(payload,Files.readAllBytes(new File(stage,"client/Wow.exe").toPath())),"six-part ZIP extracts with CRC");
            require(ClientInstaller.downloadParts(url,sizes,hashes,cache,pause,quiet).length()==archive.length,"complete cache reused");

            File legacy=root.resolve("legacy").toFile(); legacy.mkdirs();
            long boundary=sizes[0]+sizes[1];
            Files.write(new File(legacy,"client.zip.part").toPath(),Arrays.copyOf(archive,(int)boundary));
            Properties old=new Properties(); old.setProperty("url",url); old.setProperty("length",""+archive.length);
            old.setProperty("validator","\"old-single-zip\"");
            try (OutputStream out=new FileOutputStream(new File(legacy,"download.properties"))) { old.store(out,""); }
            ClientInstaller.downloadParts(url,sizes,hashes,legacy,pause,(p,d,t,x)->{
                if (p.equals("download") && x.equals("正在下载分片 3/6")) require(requestedPart.get()==2 && requestedRange.get()==0,"boundary starts next part");
            });
            require(Arrays.equals(archive,Files.readAllBytes(new File(legacy,"client.zip").toPath())),"legacy partial migrates at boundary");

            File broken=root.resolve("broken").toFile(); corrupt.set(true);
            expectFailure(()->ClientInstaller.downloadParts(url,sizes,hashes,broken,pause,quiet));
            require(new File(broken,"client.zip.part").length()==boundary,"SHA mismatch drops current part, keeps earlier verified parts");
            corrupt.set(false); missing.set(2);
            expectFailure(()->ClientInstaller.downloadParts(url,sizes,hashes,broken,pause,quiet));
            require(new File(broken,"client.zip.part").length()==boundary,"404 preserves earlier parts");
            missing.set(-1);
            require(Arrays.equals(archive,Files.readAllBytes(ClientInstaller.downloadParts(url,sizes,hashes,broken,pause,quiet).toPath())),"SHA error and 404 retry recover");
            File low=new File(root.resolve("low").toString()) { @Override public long getUsableSpace() { return 1; } };
            expectFailure(()->ClientInstaller.downloadParts(url,sizes,hashes,low,pause,quiet));
            require(!new File(low,"client.zip.part").exists(),"aggregate capacity prevents any transfer");
            System.out.println("PASS: six-part HTTP, exact Range, boundary/legacy resume, 200 restart, bad Range, SHA/404 recovery, aggregate capacity, cached reuse and ZIP extraction");
        } finally { server.stop(0); }
    }
    private static String sha(byte[] data) throws Exception {
        StringBuilder value=new StringBuilder(); for (byte b:MessageDigest.getInstance("SHA-256").digest(data)) value.append(String.format(Locale.ROOT,"%02x",b&255)); return value.toString();
    }
    private interface Operation { void run() throws Exception; }
    private static void expectFailure(Operation action) throws Exception { try { action.run(); throw new AssertionError("expected failure"); } catch (IOException expected) {} }
    private static void require(boolean condition,String message) { if (!condition) throw new AssertionError(message); }
}
