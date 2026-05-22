package cc.cc3c.hive.oss.thumbnail;

/**
 * Shared naming for thumbnail OSS key and encryption IV.
 * Thumbnail object key and the "fileName" used for IV must be consistent between upload and download.
 */
public final class ThumbnailKeyHelper {

    private static final String PREFIX = "thumb/";
    private static final String SUFFIX = "_w320.jpg";
    private static final String IV_NAME_PREFIX = "thumb_";

    private ThumbnailKeyHelper() {}

    /** OSS object key for thumbnail, e.g. thumb/{fileKey}_w320.jpg */
    public static String thumbKey(String fileKey) {
        return PREFIX + fileKey + SUFFIX;
    }

    /** File name used for encryption IV (must match on upload and download). */
    public static String thumbFileNameForEncryption(String fileKey) {
        return IV_NAME_PREFIX + fileKey + SUFFIX;
    }
}
