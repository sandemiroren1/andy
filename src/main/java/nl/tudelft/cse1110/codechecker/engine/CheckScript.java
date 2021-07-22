package nl.tudelft.cse1110.codechecker.engine;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FileASTRequestor;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class CheckScript {

    private List<CheckType> checks;

    public CheckScript(List<CheckType> checks) {
        this.checks = checks;
    }

    @Deprecated // for jackson
    public CheckScript() {}

    public List<CheckType> getChecks() {
        return Collections.unmodifiableList(checks);
    }

    @Override
    public String toString() {
        return "Configuration{" +
                "checks=" + checks +
                '}';
    }

    private int weightedChecks() {
        return checks.stream().mapToInt(check -> check.getFinalResult() ? check.getWeight() : 0).sum();
    }

    private int weights() {
        int sumOfWeights = checks.stream().mapToInt(c -> c.getWeight()).sum();
        return sumOfWeights;
    }

    public String generateReport() {
        StringBuilder report = new StringBuilder();

        report.append(String.format("%d\n", weights()));
        report.append(String.format("%d\n", weightedChecks()));
        for (CheckType check : checks) {
            report.append(String.format("%s", check.reportExecution()));
        }

        return report.toString();
    }

    public void runChecks(String sourceCodePath) {
        ASTParser parser = ASTParser.newParser(AST.JLS15);
        parser.setResolveBindings(true);
        parser.setBindingsRecovery(true);

        Map<String, String> options = JavaCore.getOptions();
        JavaCore.setComplianceOptions(JavaCore.VERSION_15, options);
        parser.setCompilerOptions(options);

        parser.setEnvironment(new String[0], new String[0], null, true);
        parser.createASTs(new String[] {sourceCodePath}, null, new String[0], new FileASTRequestor() {
            public void acceptAST(String sourceFilePath, CompilationUnit cu) {
                checks
                        .stream()
                        .forEach(cfg -> cfg.runCheck(cu));
            }
        }, null);
    }

}