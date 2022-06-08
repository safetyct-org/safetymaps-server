package nl.opengeogroep.safetymaps.utils;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static java.io.File.separator;

public class ZipIOStream {
    /**
     * Zip the files or directory.
     * @param source The path to be zipped
     * @param target The path of file as the output of zipping {@code source}
     * @param report Map of errors that occurred while zipping {@code source}. Or just pass {@code null}, if you want to throw the exception.
     * @return {@code True} if this process was successful, otherwise {@code false}.
     */
    public static boolean Zip(Path source, Path target, Map<Path, Throwable> report) {
        ZipIOStream zipStream;
        boolean b = report == null;

        try{ zipStream = new ZipIOStream(source, target); }
        catch(Throwable e) {
            e = new RuntimeException("Initializing was failed", e);
            if(b) throw (RuntimeException) e;
            report.put(null, e);
            return false;
        }

        if(!zipStream.tryLock(b))
            return false;

        zipStream.zip(source, report);

        return zipStream.close(b) && (b || report.size() < 1);
    }

    private final FileOutputStream F_OUT;
    private final ZipOutputStream Z_OUT;
    private final int N;
    private FileLock lock;

    private ZipIOStream(Path source, Path target) throws Throwable {
        if(source == null || Files.notExists(source))
            throw new IllegalArgumentException("The source never existed");
        if(target == null)
            throw new IllegalArgumentException("The target can't be null");
        F_OUT = new FileOutputStream(target.toFile());
        Z_OUT = new ZipOutputStream(F_OUT);
        N = source.getNameCount() - 1;
    }

    private void zip(Path source, Map<Path, Throwable> report) {
        final byte[] BYTES;

        if(Files.isRegularFile(source)) {
            ZipEntry entry = new ZipEntry(source.subpath(N, source.getNameCount()).toString());

            try(FileInputStream in = new FileInputStream(source.toFile())) {
                Z_OUT.putNextEntry(entry);
                BYTES = new byte[8192]; //Hold 8MB in memory.

                for(int length; (length = in.read(BYTES)) > -1;)
                    Z_OUT.write(BYTES, 0, length);
            } catch(Exception e) {
                e = new RuntimeException(source.toString(), e);
                if(report == null) throw (RuntimeException) e;
                report.put(source, e);
            }
        } else if(Files.isDirectory(source)) {
            List paths = new java.util.ArrayList<Path>();

            try(Stream stream = Files.walk(source, 1)) {
                stream.forEach(paths::add);
            } catch(Exception e) {
                e = new RuntimeException("Walking path was failed: " + source, e);
                if(report == null) throw (RuntimeException) e;
                report.put(source, e);
                paths.clear();
                return;
            }

            paths.remove(source);
            if(paths.size() > 0)
                for(Path e : (java.util.ArrayList<Path>)paths) zip(e, report);
            else {
                String name = source.subpath(N, source.getNameCount()).toString();
                try { Z_OUT.putNextEntry(new ZipEntry(name + separator)); }
                catch(Exception e) {
                    e = new RuntimeException(source.toString(), e);
                    if(report == null) throw (RuntimeException) e;
                    report.put(source, e);
                }
            }
        } else {
            if(report != null)
                report.put(source, new IllegalArgumentException("Source's neither a file nor a directory"));
        }
    }

    private boolean close(boolean thrown) {
        boolean b = false;
        try {
            if(lock != null)
                lock.close();
            Z_OUT.close();
            b = true;
        } catch(IOException e) {
            if(thrown) throw new RuntimeException("Closing resources was failed", e);
        }
        return b;
    }

    private boolean tryLock(boolean thrown) {
        try { lock = F_OUT.getChannel().tryLock(); }
        catch(Throwable e) {
            if(thrown) throw new RuntimeException("Locking the target path has failed", e);
            return false;
        }
        return true;
    }
}
