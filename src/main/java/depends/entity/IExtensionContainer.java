package depends.entity;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface IExtensionContainer {
    @NotNull Integer getId();
    @Nullable FunctionEntity lookupExtensionFunctionInVisibleScope(
            @NotNull TypeEntity type, @NotNull GenericName genericName
    );
}
