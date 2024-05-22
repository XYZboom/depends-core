package depends.utils;

import depends.entity.Entity;
import depends.entity.FileEntity;
import org.jetbrains.annotations.NotNull;

public class EntityUtils {
	public static @NotNull String getLanguage(@NotNull Entity entity) {
		if (entity instanceof FileEntity fileEntity) {
			String qualifiedName = fileEntity.getQualifiedName();
			if (qualifiedName.endsWith(".kt")) {
				return "Kotlin";
			}
			if (qualifiedName.endsWith(".java")) {
				return "Java";
			}
		}
		if (entity.getParent() == null)
			return "Unknown";
		return getLanguage(entity.getParent());
	}
}
