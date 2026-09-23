package utils;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class that merges user-submitted code with a predefined base,
 * automatically generates test cases, compiles if necessary, and executes the final result.
 *
 * <p>Compilation and execution of the submission go through a {@link Sandbox}, which
 * isolates them from the host (see {@link Sandbox} for the configuration).</p>
 */
public class FusionneurCode3 {

    private final Sandbox sandbox;

    /** Uses the sandbox configured by the environment (Docker by default). */
    public FusionneurCode3() {
        this(Sandbox.fromEnvironment());
    }

    public FusionneurCode3(Sandbox sandbox) {
        this.sandbox = sandbox;
    }

    /**
     * Class to hold the result of the code execution, including standard output,
     * standard error, and the process exit code.
     */
    public static class ResultatExecution {
        private String sortieStandard;
        private String sortieErreur;
        private int codeRetour;

        public ResultatExecution(String out, String err, int code) {
            this.sortieStandard = out;
            this.sortieErreur = err;
            this.codeRetour = code;
        }

        public String getSortieStandard() { return sortieStandard; }
        public String getSortieErreur() { return sortieErreur; }
        public int getCodeRetour() { return codeRetour; }
    }

    /**
     * Compiles and executes code submitted by the user, optionally injecting it into a base template.
     *
     * @param langage         The programming language (e.g. "java", "python", "c", "php", "javascript")
     * @param codeUtilisateur The user-submitted function or code snippet
     * @param codeBase        Optional base code to inject into (used when not empty)
     * @param ligneInsertion  The line number in base code to insert the user code
     * @return                An execution result with output, error, and exit code
     * @throws Sandbox.SandboxUnavailableException if the sandbox is required but unavailable;
     *                                             the submission is then not executed
     */
    public ResultatExecution executerCode(String langage, String codeUtilisateur, String codeBase, int ligneInsertion)
            throws IOException, InterruptedException {

        String codeFinal = "";

        // === PYTHON ===
        if (langage.equalsIgnoreCase("python")) {
            codeFinal = codeUtilisateur;
            String nomFonction = extraireNomFonctionPython(codeUtilisateur);
            if (nomFonction == null) throw new IllegalArgumentException("Cannot find a Python function.");

            String params = extraireParametresFonctionPython(codeUtilisateur, nomFonction);
            int nbArgs = params.isEmpty() ? 0 : params.split(",").length;

            StringBuilder testsCode = new StringBuilder("\n# Automatic tests\n");
            if (nbArgs == 2) {
                testsCode.append("print(").append(nomFonction).append("(2, 3))\n");
                testsCode.append("print(").append(nomFonction).append("(1, 2))\n");
            } else if (nbArgs == 1) {
                testsCode.append("print(").append(nomFonction).append("(2))\n");
                testsCode.append("print(").append(nomFonction).append("(3))\n");
            } else {
                testsCode.append("print(").append(nomFonction).append("())\n");
            }

            codeFinal += testsCode.toString();
        }

        // === C === (when no base code is provided)
        else if (langage.equalsIgnoreCase("c") && (codeBase == null || codeBase.trim().isEmpty())) {
            codeFinal = codeUtilisateur;
            String nomFonction = extraireNomFonctionC(codeUtilisateur);
            if (nomFonction == null) throw new IllegalArgumentException("Cannot find a C function.");

            boolean hasStdioInclude = codeUtilisateur.contains("#include <stdio.h>");
            boolean hasMainFunction = codeUtilisateur.contains("main(");

            if (!hasMainFunction) {
                int nbArgs = compterArguments(codeUtilisateur, nomFonction);
                StringBuilder mainCode = new StringBuilder();
                if (!hasStdioInclude) mainCode.append("#include <stdio.h>\n");

                mainCode.append("int main() {\n");
                if (nbArgs == 2) {
                    mainCode.append("    printf(\"%d\\n\", ").append(nomFonction).append("(2, 3));\n");
                    mainCode.append("    printf(\"%d\\n\", ").append(nomFonction).append("(1, 2));\n");
                } else if (nbArgs == 1) {
                    mainCode.append("    printf(\"%d\\n\", ").append(nomFonction).append("(2));\n");
                    mainCode.append("    printf(\"%d\\n\", ").append(nomFonction).append("(3));\n");
                } else {
                    mainCode.append("    printf(\"%d\\n\", ").append(nomFonction).append("());\n");
                }
                mainCode.append("    return 0;\n}\n");

                codeFinal += "\n" + mainCode;
            }
        }

        // === JAVA === (when no base code is provided)
        else if (langage.equalsIgnoreCase("java") && (codeBase == null || codeBase.trim().isEmpty())) {
            String methodName = extraireNomFonctionJava(codeUtilisateur);
            if (methodName == null) {
                throw new IllegalArgumentException("Cannot find a Java method in the user code.");
            }

            int nbArgs = compterArguments(codeUtilisateur, methodName);

            StringBuilder javaCode = new StringBuilder();
            javaCode.append("public class Main {\n\n");

            String[] lignesCode = codeUtilisateur.split("\\r?\\n");
            for (String ligne : lignesCode) {
                ligne = ligne.trim();
                if (!ligne.startsWith("System.out.println")) {
                    javaCode.append("    ").append(ligne).append("\n");
                }
            }

            javaCode.append("\n    public static void main(String[] args) {\n");
            if (nbArgs == 2) {
                javaCode.append("        System.out.println(").append(methodName).append("(2, 3));\n");
                javaCode.append("        System.out.println(").append(methodName).append("(1, 2));\n");
            } else if (nbArgs == 1) {
                javaCode.append("        System.out.println(").append(methodName).append("(2));\n");
                javaCode.append("        System.out.println(").append(methodName).append("(3));\n");
            } else {
                javaCode.append("        System.out.println(").append(methodName).append("());\n");
            }
            javaCode.append("    }\n}\n");

            codeFinal = javaCode.toString();
        }

        // === JAVASCRIPT ===
        else if (langage.equalsIgnoreCase("javascript") && (codeBase == null || codeBase.trim().isEmpty())) {
            String methodName = extraireNomFonctionJavaScript(codeUtilisateur);
            if (methodName == null) {
                throw new IllegalArgumentException("Cannot find a JavaScript function in the user code.");
            }

            int nbArgs = codeUtilisateur.contains(",") ? 2 : 1;

            StringBuilder jsCode = new StringBuilder();
            jsCode.append(codeUtilisateur);
            if (nbArgs == 2) {
                jsCode.append("\nconsole.log(").append(methodName).append("(2, 3));\n");
                jsCode.append("console.log(").append(methodName).append("(1, 2));\n");
            } else {
                jsCode.append("\nconsole.log(").append(methodName).append("(2));\n");
                jsCode.append("console.log(").append(methodName).append("(3));\n");
            }

            codeFinal = jsCode.toString();
        }

        // === PHP ===
        else if (langage.equalsIgnoreCase("php") && (codeBase == null || codeBase.trim().isEmpty())) {
            String functionName = extraireNomFonctionPHP(codeUtilisateur);
            if (functionName == null) {
                throw new IllegalArgumentException("Cannot find a PHP function in the user code.");
            }

            int nbArgs = compterArguments(codeUtilisateur, functionName);

            StringBuilder phpCode = new StringBuilder("<?php\n");
            phpCode.append(codeUtilisateur).append("\n");

            if (nbArgs == 2) {
                phpCode.append("echo ").append(functionName).append("(2, 3) ? \"1\\n\" : \"0\\n\";\n");
                phpCode.append("echo ").append(functionName).append("(1, 2) ? \"1\\n\" : \"0\\n\";\n");
            } else if (nbArgs == 1) {
                phpCode.append("echo ").append(functionName).append("(2) ? \"1\\n\" : \"0\\n\";\n");
                phpCode.append("echo ").append(functionName).append("(3) ? \"1\\n\" : \"0\\n\";\n");
            } else {
                phpCode.append("echo ").append(functionName).append("() ? \"1\\n\" : \"0\\n\";\n");
            }

            phpCode.append("?>");
            codeFinal = phpCode.toString();
        }


        // DEBUG: display the final code to be compiled and run
        System.out.println("=== CODE FINAL ===");
        System.out.println(codeFinal);

        // Compilation and execution logic: file names and commands are relative to the
        // submission directory, as seen both by the sandbox container and by the host
        String fileName;
        List<String> compileCommand = null;
        List<String> runCommand;

        switch (langage.toLowerCase()) {
            case "java":
                fileName = "Main.java";
                compileCommand = List.of("javac", "-J-XX:+UseSerialGC", fileName);
                runCommand = List.of("java", "-XX:+UseSerialGC", "-cp", ".", "Main");
                break;

            case "python":
                fileName = "script.py";
                boolean windowsHost = System.getProperty("os.name").toLowerCase().contains("win");
                String python = sandbox.getMode() == Sandbox.Mode.NONE && windowsHost ? "python" : "python3";
                runCommand = List.of(python, fileName);
                break;

            case "c":
                fileName = "program.c";
                compileCommand = List.of("gcc", fileName, "-o", "program.exe");
                runCommand = List.of("./program.exe");
                break;

            case "javascript":
                fileName = "script.js";
                runCommand = List.of("node", fileName);
                break;

            case "php":
                fileName = "script.php";
                runCommand = List.of("php", fileName);
                break;

            default:
                throw new IllegalArgumentException("Unsupported language: " + langage);
        }

        Path tempDir = Files.createTempDirectory("fusion_");
        try {
            Files.writeString(tempDir.resolve(fileName), codeFinal);

            // Compile if required (the compiler may write to the submission directory)
            if (compileCommand != null) {
                Sandbox.Result compilation = sandbox.run(tempDir, compileCommand, true);
                if (compilation.exitCode() != 0) {
                    return new ResultatExecution("", compilation.stderr(), compilation.exitCode());
                }
            }

            // Run the compiled or interpreted program (read-only submission directory)
            Sandbox.Result execution = sandbox.run(tempDir, runCommand, false);
            return new ResultatExecution(execution.stdout(), execution.stderr(), execution.exitCode());
        } finally {
            try {
                deleteDirectory(tempDir.toFile());
            } catch (IOException e) {
                System.err.println("Cleanup error: " + e.getMessage());
            }
        }
    }

    // ========================
    //        UTILITIES
    // ========================

    /** Extracts function name from Python code */
    private String extraireNomFonctionPython(String code) {
        Pattern pattern = Pattern.compile("def\\s+(\\w+)\\s*\\(");
        Matcher matcher = pattern.matcher(code);
        return matcher.find() ? matcher.group(1) : null;
    }

    /** Extracts parameters of a Python function */
    private String extraireParametresFonctionPython(String code, String nomFonction) {
        Pattern pattern = Pattern.compile("def\\s+" + nomFonction + "\\s*\\(([^)]*)\\)");
        Matcher matcher = pattern.matcher(code);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    /** Extracts C function name from code */
    private String extraireNomFonctionC(String code) {
        Pattern def = Pattern.compile("\\w+\\s+(\\w+)\\s*\\([^)]*\\)\\s*\\{");
        Matcher matcherDef = def.matcher(code);
        if (matcherDef.find()) return matcherDef.group(1);

        Pattern decl = Pattern.compile("\\w+\\s+(\\w+)\\s*\\([^)]*\\)\\s*;");
        Matcher matcherDecl = decl.matcher(code);
        return matcherDecl.find() ? matcherDecl.group(1) : null;
    }

    /** Extracts function name from Java method */
    private String extraireNomFonctionJava(String code) {
        Pattern pattern = Pattern.compile("(public|private|protected|static|\\s)+\\s+[\\w<>\\[\\]]+\\s+(\\w+)\\s*\\(");
        Matcher matcher = pattern.matcher(code);
        return matcher.find() ? matcher.group(2) : null;
    }

    /**
     * Inserts a block of code at a specified line in a base code string.
     *
     * @param codeBase      The original code into which to insert
     * @param codeAInserer  The code block to insert
     * @param ligneInsertion Line number where to insert the new code
     * @return Combined code with the inserted snippet
     */
    private String insererALigne(String codeBase, String codeAInserer, int ligneInsertion) {
        if (ligneInsertion < 0) return codeAInserer + "\n" + codeBase;

        String[] lignes = codeBase.split("\n");
        StringBuilder resultat = new StringBuilder();
        for (int i = 0; i < lignes.length; i++) {
            if (i == ligneInsertion) resultat.append(codeAInserer).append("\n");
            resultat.append(lignes[i]).append("\n");
        }
        if (ligneInsertion >= lignes.length) resultat.append(codeAInserer).append("\n");
        return resultat.toString();
    }

    /** Extracts JavaScript function name */
    private String extraireNomFonctionJavaScript(String code) {
        Pattern pattern = Pattern.compile("function\\s+(\\w+)\\s*\\(");
        Matcher matcher = pattern.matcher(code);
        return matcher.find() ? matcher.group(1) : null;
    }

    /** Extracts PHP function name */
    private String extraireNomFonctionPHP(String code) {
        Pattern pattern = Pattern.compile("function\\s+(\\w+)\\s*\\(");
        Matcher matcher = pattern.matcher(code);
        return matcher.find() ? matcher.group(1) : null;
    }

    /** Counts the number of arguments in a function call */
    private int compterArguments(String code, String functionName) {
        Pattern pattern = Pattern.compile(functionName + "\\s*\\(([^)]*)\\)");
        Matcher matcher = pattern.matcher(code);
        if (matcher.find()) {
            String params = matcher.group(1).trim();
            if (params.isEmpty()) return 0;
            return params.split(",").length;
        }
        return 0;
    }

    /**
     * Recursively deletes a directory and its content.
     *
     * @param file The root directory or file to delete
     * @throws IOException If a file or directory cannot be deleted
     */
    private void deleteDirectory(File file) throws IOException {
        if (file.isDirectory()) {
            File[] entries = file.listFiles();
            if (entries != null) {
                for (File entry : entries) deleteDirectory(entry);
            }
        }
        if (!file.delete()) throw new IOException("Unable to delete " + file);
    }
}
