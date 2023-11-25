package depends.extractor;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.ParseTreeListener;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.jetbrains.annotations.NotNull;

public interface IFileListener extends ParseTreeListener {
	/**
	 * invoke before parse a file
	 *
	 * @param filePath the path of the file
	 * @return false if skip parse this file
	 */
	boolean enterFile(@NotNull String filePath);

	void exitFile(@NotNull String filePath);

	@Override
	default void visitTerminal(TerminalNode node) {
	}

	@Override
	default void visitErrorNode(ErrorNode node) {
	}

	@Override
	default void enterEveryRule(ParserRuleContext ctx) {
	}

	@Override
	default void exitEveryRule(ParserRuleContext ctx) {
	}
}
