package depends.extractor.java;

import java.io.IOException;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

import depends.entity.repo.EntityRepo;
import depends.extractor.java.JavaLexer;
import depends.extractor.java.JavaParser;
import depends.relations.Inferer;


public class JavaFileParser implements depends.extractor.FileParser{
	private String fileFullPath;
	private EntityRepo entityRepo;
	private Inferer inferer;
	public JavaFileParser(String fileFullPath,EntityRepo entityRepo, Inferer inferer) {
        this.fileFullPath = fileFullPath;
        this.entityRepo = entityRepo;
        this.inferer = inferer;
	}

	@Override
	public void parse() throws IOException {
        CharStream input = CharStreams.fromFileName(fileFullPath);
        Lexer lexer = new JavaLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        JavaParser parser = new JavaParser(tokens);
        JavaListener bridge = new JavaListener(fileFullPath, entityRepo,inferer);
	    ParseTreeWalker walker = new ParseTreeWalker();
	    walker.walk(bridge, parser.compilationUnit());
    }
	
}