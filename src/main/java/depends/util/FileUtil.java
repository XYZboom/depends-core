package depends.util;

import java.io.File;
import java.io.IOException;

public class FileUtil {
	public static String uniqFilePath(String filePath) {
		try {
			File f = new File(filePath);
			filePath = f.getCanonicalPath();
		} catch (IOException e) {
		}
		return filePath;
	}
}
