package nl.tudelft.cse1110.andy.grader.execution.step;

import nl.tudelft.cse1110.andy.grader.config.Configuration;
import nl.tudelft.cse1110.andy.grader.config.DirectoryConfiguration;
import nl.tudelft.cse1110.andy.grader.config.MetaTest;
import nl.tudelft.cse1110.andy.grader.config.RunConfiguration;
import nl.tudelft.cse1110.andy.grader.execution.ExecutionFlow;
import nl.tudelft.cse1110.andy.grader.execution.ExecutionStep;
import nl.tudelft.cse1110.andy.grader.result.ResultBuilder;
import nl.tudelft.cse1110.andy.grader.util.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static nl.tudelft.cse1110.andy.grader.util.FileUtils.createTemporaryDirectory;
import static nl.tudelft.cse1110.andy.grader.util.FileUtils.deleteDirectory;

public class RunMetaTestsStep implements ExecutionStep {

    @Override
    public void execute(Configuration cfg, ResultBuilder result) {
        DirectoryConfiguration dirCfg = cfg.getDirectoryConfiguration();
        RunConfiguration runCfg = cfg.getRunConfiguration();

        int score = 0;

        try {
            List<MetaTest> metaTests = runCfg.metaTests();
            String solutionFile = FileUtils.findSolution(dirCfg.getWorkingDir());
            List<String> failures = new ArrayList<>();

            /*
             * For each meta test, we basically perform a string replace in the
             * original library code, recompile it, and run the tests.
             * If there's a failing test, the meta test is killed.
             *
             * We reuse our execution framework to run the code with the meta test.
             */
            ClassLoader currentClassLoader = Thread.currentThread().getContextClassLoader();

            for (MetaTest metaTest : metaTests) {
                Thread.currentThread().setContextClassLoader(cfg.getCleanClassloader());

                /* Copy the library and replace the library by the meta test */
                File metaWorkingDir = createTemporaryDirectory("metaWorkplace").toFile();
                FileUtils.copyFile(solutionFile, metaWorkingDir.getAbsolutePath());
                String metaFileContent = generateMetaFileContent(metaTest, dirCfg);
                createMetaTestFile(metaWorkingDir, metaFileContent);

                /* We then run the meta test, using our infrastructure */
                ResultBuilder metaResult = runMetaTest(dirCfg, metaWorkingDir);

                /* And check the result. If there's a failing test, the test suite is good! */
                int testsRan = metaResult.getTestsRan();
                int testsSucceeded = metaResult.getTestsSucceeded();
                boolean passesTheMetaTest = testsSucceeded < testsRan;

                if (passesTheMetaTest) {
                    score++;
                } else {
                    String metaName = metaTest.getName();
                    failures.add(metaName);
                }

                /* Clean up the directory */
                deleteDirectory(metaWorkingDir);
            }

            result.logMetaTests(score, metaTests.size(), failures);

            /* restore the class loader to the one before meta tests */
            Thread.currentThread().setContextClassLoader(currentClassLoader);
        } catch (Exception ex) {
            result.genericFailure(this, ex);
        }
    }

    private String generateMetaFileContent(MetaTest metaTest, DirectoryConfiguration dirCfg) {
        File libraryFile = new File(FileUtils.findLibrary(dirCfg.getWorkingDir()));
        String originalLibraryContent = FileUtils.readFile(libraryFile);
        String metaFileContent = metaTest.evaluate(originalLibraryContent);

        if (metaFileContent.equals(originalLibraryContent)) {
            throw new RuntimeException("Meta test " + metaTest.getName() + " failed to replace code.");
        }

        return metaFileContent;
    }

    private void createMetaTestFile(File metaWorkingDir, String metaFileContent) throws Exception {
        File metaFile = new File(metaWorkingDir + "/Library.java");
        if (!metaFile.createNewFile()) {
            throw new IOException("Failed to create a meta file.");
        }
        FileUtils.writeToFile(metaFile, metaFileContent);
    }

    private ResultBuilder runMetaTest(DirectoryConfiguration dirCfg, File metaWorkingDir) {
        DirectoryConfiguration metaDirCfg = new DirectoryConfiguration(
                metaWorkingDir.toString(),
                dirCfg.getOutputDir()
        );

        Configuration metaCfg = new Configuration();
        metaCfg.setDirectoryConfiguration(metaDirCfg);

        ResultBuilder metaResult = new ResultBuilder();

        ExecutionFlow flow = ExecutionFlow.justTests(metaCfg, metaResult);
        flow.run();

        return metaResult;
    }

}