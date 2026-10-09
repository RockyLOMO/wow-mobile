package com.winlator.wowmobile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public final class ConsolePortIsolationTest {
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("olddream-mode-");
        File addons = root.resolve("Interface/AddOns").toFile();
        File cp = new File(addons, "ConsolePort/Core/custom.lua");
        cp.getParentFile().mkdirs();
        byte[] custom = new byte[]{0, 12, 35, (byte)255};
        Files.write(cp.toPath(), custom);
        File other = new File(addons, "BigFoot/test.lua");
        other.getParentFile().mkdirs();
        Files.write(other.toPath(), new byte[]{42});
        require(ConsolePortIsolation.apply(root.toFile(), true), "native isolation");
        require(!new File(addons, "ConsolePort").exists(), "outside addon loader");
        require(other.isFile(), "other addons preserved");
        require(ConsolePortIsolation.apply(root.toFile(), true), "native idempotent");
        require(ConsolePortIsolation.apply(root.toFile(), false), "legacy restore");
        require(Arrays.equals(custom, Files.readAllBytes(cp.toPath())), "custom bytes preserved");
        require(ConsolePortIsolation.apply(root.toFile(), false), "legacy idempotent");
        require(ConsolePortIsolation.apply(root.toFile(), true), "second switch");
        cp.getParentFile().mkdirs();
        Files.write(cp.toPath(), new byte[]{7});
        require(!ConsolePortIsolation.apply(root.toFile(), false), "conflict refused");
        require(Arrays.equals(new byte[]{7}, Files.readAllBytes(cp.toPath())), "conflicting file untouched");
        require(Arrays.equals(custom, Files.readAllBytes(root.resolve(".olddream-consoleport/ConsolePort/Core/custom.lua"))), "saved original untouched");
        System.out.println("PASS: Native isolation, Legacy restoration, idempotency, custom bytes and conflict protection");
    }
    private static void require(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
}
