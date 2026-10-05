package com.winlator.wowmobile;

import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.zip.*;

/** Real HTTP/file regression tests, without downloading the 20GB production client. */
public final class ClientInstallerTest {
    private static byte[] body;
    private static String etag = "\"fixture-v1\"";
    private static final AtomicLong lastRange = new AtomicLong();
    private static boolean ignoreRange;
    private static final ClientInstaller.ReadyCheck READY = dir -> new File(dir,"Wow.exe").length()>0;
    public static void main(String[] args) throws Exception {
        Path root=Files.createTempDirectory(Paths.get(args[0]),"installer-test-");
        require(ClientInstaller.lowSpaceMessage(19_999_999_999L)!=null,"below 20GB rejected");
        require(ClientInstaller.lowSpaceMessage(20_000_000_000L)==null,"20GB minimum boundary");
        require(ClientInstaller.lowSpaceMessage(0).contains("45 GB"),"low space explains download plus extraction");
        long available=ClientInstaller.availableSpace(root.resolve("missing/a/cache").toFile());
        require(available>0 && Math.abs(available-root.toFile().getUsableSpace())<16*1024*1024L,"missing cache checks existing storage parent");
        byte[] payload=new byte[4*1024*1024];new Random(335).nextBytes(payload);
        body=zip("WoW-3.3.5a-zhCN/Wow.exe",payload);
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/client.zip", exchange -> {
            byte[] current=body; exchange.getResponseHeaders().set("ETag",etag);
            exchange.getResponseHeaders().set("Accept-Ranges","bytes");
            if (exchange.getRequestMethod().equals("HEAD")) {
                exchange.getResponseHeaders().set("Content-Length",""+current.length);
                exchange.sendResponseHeaders(200,-1);exchange.close();return;
            }
            String range=exchange.getRequestHeaders().getFirst("Range");
            int start=range==null || ignoreRange ? 0:Integer.parseInt(range.substring(6,range.length()-1));
            lastRange.set(start);
            if(start>0) exchange.getResponseHeaders().set("Content-Range","bytes "+start+"-"+(current.length-1)+"/"+current.length);
            exchange.sendResponseHeaders(start>0?206:200,current.length-start);
            try(OutputStream out=exchange.getResponseBody()) { out.write(current,start,current.length-start); }
            catch(IOException ignored) {} finally {exchange.close();}
        });
        server.start();
        String url="http://127.0.0.1:"+server.getAddress().getPort()+"/client.zip";
        try {
            File lowSpace = new File(root.resolve("low-space").toString()) {
                @Override public long getUsableSpace() { return 1; }
            };
            expectFailure(()->ClientInstaller.download(url,lowSpace,new AtomicBoolean(),(p,d,t,x)->{}));
            require(!new File(lowSpace,"client.zip.part").exists(),"dynamic space check prevents download file creation");
            File cache=root.resolve("cache").toFile(), destination=root.resolve("client").toFile();
            AtomicBoolean pause=new AtomicBoolean();
            try {ClientInstaller.install(url,cache,destination,pause,(phase,done,total,detail)-> {if(phase.equals("download")&&done>0)pause.set(true);},READY);throw new AssertionError("pause ignored");}
            catch(ClientInstaller.Paused expected) {}
            long partial=new File(cache,"client.zip.part").length();
            require(partial>0&&partial<body.length,"partial saved");pause.set(false);
            ClientInstaller.install(url,cache,destination,pause,(p,d,t,x)->{},READY);
            require(lastRange.get()==partial,"resumed exact offset");
            require(Arrays.equals(payload,Files.readAllBytes(new File(destination,"Wow.exe").toPath())),"extracted payload");
            require(!new File(cache,"client.zip").exists(),"successful archive cleanup");
            ClientInstaller.install(url,cache,destination,pause,(p,d,t,x)->{},READY);
            require(Arrays.equals(payload,Files.readAllBytes(new File(destination,"Wow.exe").toPath())),"existing installation preserved");

            File resumeCache=root.resolve("resume").toFile();resumeCache.mkdirs();
            pause.set(false);
            try {ClientInstaller.download(url,resumeCache,pause,(p,d,t,x)->{if(d>0)pause.set(true);});}
            catch(ClientInstaller.Paused expected) {}
            pause.set(false);ignoreRange=true;
            File archive=ClientInstaller.download(url,resumeCache,pause,(p,d,t,x)->{});
            require(Arrays.equals(body,Files.readAllBytes(archive.toPath())),"HTTP 200 restart truncates partial");ignoreRange=false;
            etag="\"fixture-v2\"";body=zip("WoW-3.3.5a-zhCN/Wow.exe",new byte[]{3,3,5});
            archive=ClientInstaller.download(url,resumeCache,pause,(p,d,t,x)->{});
            require(Arrays.equals(body,Files.readAllBytes(archive.toPath())),"changed ETag resets cache");

            File occupied=root.resolve("occupied").toFile();occupied.mkdirs();
            File userFile=new File(occupied,"notes.txt");Files.write(userFile.toPath(),new byte[]{7});
            expectFailure(()->ClientInstaller.install(url,cache,occupied,pause,(p,d,t,x)->{},READY));
            require(userFile.length()==1,"user files preserved");
            for(String bad:new String[]{"../escape","a/../Wow.exe","/absolute","C:/drive"}) {
                File unsafe=root.resolve("unsafe.zip").toFile();Files.write(unsafe.toPath(),zip(bad,new byte[]{1}));
                File stage=root.resolve("stage").toFile();stage.mkdirs();
                expectFailure(()->ClientInstaller.extract(unsafe,stage,pause,(p,d,t,x)->{}));
            }
            body=zip("WoW-3.3.5a-zhCN/Wow.exe",payload);
            // A STORE payload byte changes without updating the directory's CRC.
            body[80]^=1;etag="\"corrupt\"";
            File corruptCache=root.resolve("corrupt").toFile();
            expectFailure(()->ClientInstaller.install(url,corruptCache,root.resolve("bad-client").toFile(),pause,(p,d,t,x)->{},READY));
            require(!new File(corruptCache,"client.zip").exists(),"CRC error invalidates cache");
            body=new byte[]{1,2,3}; etag="\"bad-directory\"";
            expectFailure(()->ClientInstaller.install(url,corruptCache,root.resolve("bad-client").toFile(),pause,(p,d,t,x)->{},READY));
            require(!new File(corruptCache,"client.zip").exists(),"invalid directory invalidates cache");
            require(!ClientInstaller.isClientReady(destination),"small fixture not mistaken for real client");
            System.out.println("PASS: 20GB boundary, missing cache storage, insufficient space blocks transfer, Range resume, 200 restart, ETag change, CRC rejection/retry, safe paths, wrapper publish and user data preservation");
        } finally {server.stop(0);}
    }
    private static byte[] zip(String name,byte[] data)throws IOException {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(ZipOutputStream zip=new ZipOutputStream(bytes)) {
            ZipEntry entry=new ZipEntry(name);entry.setMethod(ZipEntry.STORED);entry.setSize(data.length);
            CRC32 crc=new CRC32();crc.update(data);entry.setCrc(crc.getValue());zip.putNextEntry(entry);zip.write(data);zip.closeEntry();
        }
        return bytes.toByteArray();
    }
    private interface Operation {void run()throws Exception;}
    private static void expectFailure(Operation action)throws Exception {try {action.run();throw new AssertionError("expected failure");}catch(IOException expected){}}
    private static void require(boolean condition,String message) {if(!condition)throw new AssertionError(message);}
}
