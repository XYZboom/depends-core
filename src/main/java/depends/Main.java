/*
MIT License

Copyright (c) 2018-2019 Gang ZHANG

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/

package depends;

import depends.addons.DV8MappingFileBuilder;
import depends.deptypes.DependencyType;
import depends.entity.*;
import depends.entity.repo.EntityRepo;
import depends.extractor.AbstractLangProcessor;
import depends.extractor.LangProcessorRegistration;
import depends.extractor.UnsolvedBindings;
import depends.format.DependencyDumper;
import depends.format.detail.UnsolvedSymbolDumper;
import depends.generator.*;
import depends.matrix.core.DependencyMatrix;
import depends.relations.IBindingResolver;
import depends.relations.Relation;
import depends.relations.RelationCounter;
import multilang.depends.util.file.FileUtil;
import multilang.depends.util.file.FolderCollector;
import multilang.depends.util.file.TemporaryFile;
import multilang.depends.util.file.path.*;
import multilang.depends.util.file.strip.LeadingNameStripper;
import net.sf.ehcache.CacheManager;
import org.codehaus.plexus.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.PicocliException;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * The entry pooint of depends
 */
public class Main {

	private final static Logger logger = LoggerFactory.getLogger(Main.class);

	public static void main(String[] args) {
		try {
			LangRegister.register();
			DependsCommand appArgs = CommandLine.populateCommand(new DependsCommand(), args);
			if (appArgs.help) {
				CommandLine.usage(new DependsCommand(), System.out);
				System.exit(0);
			}
			verifyParameters(appArgs);
			executeCommand(appArgs);
		} catch (Exception e) {
			if (e instanceof PicocliException) {
				CommandLine.usage(new DependsCommand(), System.out);
			} else if (e instanceof ParameterException) {
				System.err.println(e.getMessage());
			} else {
				System.err.println("Exception encountered. If it is a design error, please report issue to us.");
				e.printStackTrace();
			}
			System.exit(0);
		}
	}

	private static void verifyParameters(DependsCommand args) throws ParameterException {
		String[] granularities = args.getGranularity();
		List<String> validGranularities = Arrays.asList("file", "method", "structure");
		for (String g : granularities) {
			if (!validGranularities.contains(g)) {
				throw new ParameterException("granularity is invalid:" + g);
			}
		}
	}

	private static void executeCommand(DependsCommand args) throws ParameterException {
		String lang = args.getLang();
		String inputDir = args.getSrc();
		String[] includeDir = args.getIncludes();
		String outputName = args.getOutputName();
		String outputDir = args.getOutputDir();
		String[] outputFormat = args.getFormat();

		inputDir = FileUtil.uniqFilePath(inputDir);

		if (args.isAutoInclude()) {
			includeDir = appendAllFoldersToIncludePath(inputDir, includeDir);
		}

		AbstractLangProcessor langProcessor = LangProcessorRegistration.getRegistry().getProcessorOf(lang);
		if (langProcessor == null) {
			System.err.println("Not support this language: " + lang);
			return;
		}

		IBindingResolver bindingResolver = langProcessor.createBindingResolver(args.isOutputExternalDependencies(), args.isDuckTypingDeduce());

		long startTime = System.currentTimeMillis();
		//step1: build data
		EntityRepo entityRepo = langProcessor.buildDependencies(inputDir, includeDir, bindingResolver);
		new RelationCounter(entityRepo, langProcessor, bindingResolver).computeRelations();
		System.out.println("Dependency done....");
		logDependencyInfo(entityRepo);

		//step2: generate dependencies matrix
		List<DependencyGenerator> dependencyGenerators = getDependencyGenerators(args, inputDir);
		for (DependencyGenerator dependencyGenerator : dependencyGenerators) {
			DependencyMatrix matrix = dependencyGenerator.identifyDependencies(entityRepo, args.getTypeFilter());
			DependencyDumper output = new DependencyDumper(matrix);
			output.outputResult(outputName + "-" + dependencyGenerator.getType(), outputDir, outputFormat);
		}

		if (args.isOutputExternalDependencies()) {
			Set<UnsolvedBindings> unsolved = langProcessor.getExternalDependencies();
			UnsolvedSymbolDumper unsolvedSymbolDumper = new UnsolvedSymbolDumper(unsolved, args.getOutputName(), args.getOutputDir(),
					new LeadingNameStripper(args.isStripLeadingPath(), inputDir, args.getStrippedPaths()));
			unsolvedSymbolDumper.output();
		}
		long endTime = System.currentTimeMillis();
		TemporaryFile.getInstance().delete();
		CacheManager.create().shutdown();
		System.out.println("Consumed time: " + (float) ((endTime - startTime) / 1000.00) + " s,  or "
				+ (float) ((endTime - startTime) / 60000.00) + " min.");
		if (args.isDv8map()) {
			DV8MappingFileBuilder dv8MapfileBuilder = new DV8MappingFileBuilder(langProcessor.supportedRelations());
			dv8MapfileBuilder.create(outputDir + File.separator + "depends-dv8map.mapping");
		}
	}

	private static void logDependencyInfo(EntityRepo entityRepo) {
		int packageCount = 0;
		int fileCount = 0;
		int methodCount = 0;
		int classCount = 0;
		int varCount = 0;
		Iterator<Entity> entityIterator = entityRepo.entityIterator();
		ArrayList<String> strings = DependencyType.allDependencies();
		Map<String, Integer> dependencyCount = strings.stream().collect(Collectors.toMap(key -> key, value -> 0));
		while (entityIterator.hasNext()) {
			Entity entity = entityIterator.next();
			if (entity instanceof PackageEntity) {
				packageCount++;
			} else if (entity instanceof FileEntity) {
				fileCount++;
			} else if (entity instanceof FunctionEntity) {
				methodCount++;
			} else if (entity instanceof TypeEntity) {
				classCount++;
			} else if (entity instanceof VarEntity) {
				varCount++;
			}
			ArrayList<Relation> relations = entity.getRelations();
			for (Relation relation : relations) {
				String relationType = relation.getType();
				if (dependencyCount.containsKey(relationType)) {
					dependencyCount.put(relationType, dependencyCount.get(relationType) + 1);
				}
			}
		}
		logger.info("Packages: {}", packageCount);
		logger.info("Files: {}", fileCount);
		logger.info("Classes: {}", classCount);
		logger.info("Methods: {}", methodCount);
		logger.info("Vars: {}", varCount);
		for (Map.Entry<String,Integer> entry : dependencyCount.entrySet()) {
			logger.info("{}: {}", entry.getKey(), entry.getValue());
		}
	}

	private static String[] appendAllFoldersToIncludePath(String inputDir, String[] includeDir) {
		FolderCollector includePathCollector = new FolderCollector();
		List<String> additionalIncludePaths = includePathCollector.getFolders(inputDir);
		additionalIncludePaths.addAll(Arrays.asList(includeDir));
		includeDir = additionalIncludePaths.toArray(new String[]{});
		return includeDir;
	}

	private static List<DependencyGenerator> getDependencyGenerators(DependsCommand app, String inputDir) throws ParameterException {
		FilenameWritter filenameWritter = new EmptyFilenameWritter();
		if (!StringUtils.isEmpty(app.getNamePathPattern())) {
			if (app.getNamePathPattern().equals("dot") ||
					app.getNamePathPattern().equals(".")) {
				filenameWritter = new DotPathFilenameWritter();
			} else if (app.getNamePathPattern().equals("unix") ||
					app.getNamePathPattern().equals("/")) {
				filenameWritter = new UnixPathFilenameWritter();
			} else if (app.getNamePathPattern().equals("windows") ||
					app.getNamePathPattern().equals("\\")) {
				filenameWritter = new WindowsPathFilenameWritter();
			} else {
				throw new ParameterException("Unknown name pattern paremater:" + app.getNamePathPattern());
			}
		}

		List<DependencyGenerator> dependencyGenerators = new ArrayList<>();
		for (int i = 0; i < app.getGranularity().length; i++) {
			/* by default use file dependency generator */
			DependencyGenerator dependencyGenerator = null;
			/* method parameter means use method generator */
			if (app.getGranularity()[i].equals("method"))
				dependencyGenerator = new FunctionDependencyGenerator();
			else if (app.getGranularity()[i].equals("file"))
				dependencyGenerator = new FileDependencyGenerator();
			else if (app.getGranularity()[i].equals("structure"))
				dependencyGenerator = new StructureDependencyGenerator();

			dependencyGenerators.add(dependencyGenerator);
			if (app.isStripLeadingPath() ||
					app.getStrippedPaths().length > 0) {
				dependencyGenerator.setLeadingStripper(new LeadingNameStripper(app.isStripLeadingPath(), inputDir, app.getStrippedPaths()));
			}
			if (app.isDetail()) {
				dependencyGenerator.setGenerateDetail(true);
			}
			dependencyGenerator.setOutputSelfDependencies(app.isOutputSelfDependencies());
			dependencyGenerator.setFilenameRewritter(filenameWritter);
		}
		return dependencyGenerators;
	}

}
