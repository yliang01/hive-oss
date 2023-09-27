package cc.cc3c.hive.oss.vendor.util;

import org.apache.commons.io.FileUtils;

import java.io.*;
import java.util.Collection;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class ZipUtils {

    //    private static final Set<String> suffixSet = new HashSet<>(Arrays.asList("mkv", "mp4", "ass", "srt"));
    private static final int bufferSize = 1024 * 1024;

    public static File compress(File folder) throws Exception {
        if (!folder.isDirectory()) {
            return null;
        }
//        File[] files = folder.listFiles(new HiveFileFilter());
        Collection<File> fileCollection = FileUtils.listFiles(folder, null, true);

        if (fileCollection.isEmpty()) {
            return null;
        }

        int start = folder.getCanonicalPath().length();

        File zip = new File(folder.getPath() + ".zip.hive");
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zip), bufferSize))) {
            zipOutputStream.setLevel(Deflater.NO_COMPRESSION);
            for (File file : fileCollection) {
                String name = file.getCanonicalPath().substring(start);
                System.out.println(name);
                zipOutputStream.putNextEntry(new ZipEntry(name));
                try (BufferedInputStream bufferedInputStream = new BufferedInputStream(new FileInputStream(file), bufferSize)) {
                    byte[] buffer = new byte[bufferSize];
                    int length;
                    while ((length = bufferedInputStream.read(buffer, 0, bufferSize)) != -1) {
                        zipOutputStream.write(buffer, 0, length);
                    }
                    zipOutputStream.closeEntry();
                }
            }
        }
        return zip;
    }

    public static File decompress(File zipFile, File targetFolder) throws FileNotFoundException {
        if (!(zipFile.isFile() && targetFolder.isDirectory())) {
            return null;
        }
        try (ZipInputStream zipInputStream = new ZipInputStream(new BufferedInputStream(new FileInputStream(zipFile), bufferSize));
        ) {
            ZipEntry zipEntry;
            while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                System.out.println(zipEntry.getName());
                int l;
                byte[] buffer = new byte[bufferSize];
                String path = targetFolder.getCanonicalPath() + File.separator + zipEntry.getName();
                try (BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(new FileOutputStream(path), bufferSize)) {
                    while ((l = zipInputStream.read(buffer, 0, bufferSize)) != -1) {
                        bufferedOutputStream.write(buffer, 0, l);
                    }
                    zipInputStream.closeEntry();
                }
            }
            return targetFolder;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

//    private static class HiveFileFilter implements FileFilter {
//        @Override
//        public boolean accept(File pathname) {
//            if (pathname.isDirectory()) {
//                return false;
//            }
//            int p = pathname.getName().lastIndexOf(".");
//            return suffixSet.contains(pathname.getName().substring(p + 1));
//        }
//    }
}
