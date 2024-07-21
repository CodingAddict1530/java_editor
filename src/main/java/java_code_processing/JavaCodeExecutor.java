package java_code_processing;

import custom_classes.ConsoleTextArea;
import custom_classes.RootTreeNode;

import javax.tools.*;
import java.io.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

public class JavaCodeExecutor {

    private static Process process;
    private static BufferedWriter bufferedWriter;

    // 1 Couldn't read file
    // 2 Compilation failed
    // 3 Couldn't delete
    public static int run(File file, ConsoleTextArea textArea, RootTreeNode project) {

        int returnValue = 0;
        String sourceCode = readSourceFile(file);
        if (sourceCode == null) {
            return 1;
        }
        Path buildPath;
        if (project == null) {
            buildPath = null;
        } else {
            buildPath = new File(project.getPath().toString(), "bin").toPath();
        }

        boolean compile = compile(sourceCode, file, textArea, (buildPath == null) ? null :
                buildPath.toString());
        if (compile) {
            setUpInputHandling(textArea);
            execute(file, textArea, buildPath);
        } else {
            returnValue = 2;
        }
        return returnValue;
    }

    private static void execute(File file, ConsoleTextArea textArea, Path buildPath) {

        String[] parts1 = file.getName().split("\\.");
        String[] parts2 = file.getPath().split("\\\\");
        ArrayList<String> fromSrc = new ArrayList<>();
        boolean start = false;
        for (String part : parts2) {
            if (part.equals("src") || start) {
                if (start) {
                    fromSrc.add(part);
                }
                start = true;
            }
        }
        if (fromSrc.isEmpty() && buildPath != null) {
            System.out.println("File not in src!");
            return;
        }
        fromSrc.remove(fromSrc.size() - 1);
        StringBuilder filePath = new StringBuilder();
        for (String part : fromSrc) {
            filePath.append(part).append(".");
        }

        ProcessBuilder processBuilder = new ProcessBuilder("java", "-cp",
                ".", (filePath.isEmpty()) ? parts1[0] : filePath + parts1[0]);
        processBuilder.directory((buildPath == null) ? new File("src/main/classes") :
                buildPath.toFile());
        processBuilder.redirectErrorStream(true);

        try {
            process = processBuilder.start();
            bufferedWriter = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));

            // Thread to read output to textArea
            Thread outputThread = new Thread(() -> {
                try (InputStream inputStream = process.getInputStream();
                     BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream))) {

                    String line;
                    while ((line = bufferedReader.readLine()) != null) {
                        // Update TextArea on JavaFX Application Thread
                        String finalLine = line;
                        javafx.application.Platform.runLater(() -> appendStyledText(textArea, finalLine + "\n", "black"));
                    }
                } catch (IOException e) {
                    System.out.println(e.getMessage());
                }
            });

            outputThread.start();

            // Thread to check exit status
            Thread statusThread = new Thread(() -> {

                try {
                    int exitCode = process.waitFor();
                    outputThread.join();
                    javafx.application.Platform.runLater(() -> textArea.appendText("\nProcess Finished with exit code " + exitCode + "\n"));
                    if (buildPath == null) {
                        new File("src/main/classes/" + parts1[0] + ".class").delete();
                    }
                } catch (Exception e) {
                    System.out.println(e.getMessage());
                }

            });

            statusThread.start();

        } catch (Exception e) {
            System.out.println(e.getMessage());
        }

    }

    private static void setUpInputHandling(ConsoleTextArea textArea) {

        textArea.setOnKeyPressed(event -> {
            if (event.getCode().toString().equals("ENTER")) {
                String input = getUserInput(textArea);
                try {
                    bufferedWriter.write(input + System.lineSeparator());
                    bufferedWriter.flush();
                } catch (Exception e) {
                    System.out.println(e.getMessage());
                }
                textArea.setStyle(textArea.getCaretPosition(), textArea.getCaretPosition(),
                        "-fx-fill: black;");
            }
        });

    }

    private static String getUserInput(ConsoleTextArea textArea) {

        int caretPosition = textArea.getCaretPosition();
        int start = caretPosition;

        for (int i = caretPosition - 1; i >= 0; i--) {
            String style = textArea.getStyleAtPosition(i);
            if (style.contains("-fx-fill: green;")) {
                start = i;
            } else {
                break;
            }
        }
        return textArea.getText(start, caretPosition);

    }

    private static boolean compile(String sourceCode,  File file, ConsoleTextArea textArea,
                                   String buildPath) {

        // Get compiler
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            System.out.println("No Compiler");
            return false;
        }

        // Create file in memory
        JavaFileObject fileObject = new SimpleJavaFileObject(
                new File(file.getName()).toURI(),
                JavaFileObject.Kind.SOURCE
        ) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return sourceCode;
            }
        };

        // Prepare compilation task
        JavaFileManager fileManager = new ForwardingJavaFileManager<>(
                compiler.getStandardFileManager(null, null, null)
        ) {};

        // Set up writer
        StringWriter stdOutWriter = new StringWriter();
        StringWriter stdErrWriter = new StringWriter();
        PrintWriter stdOut = new PrintWriter(stdOutWriter);
        PrintWriter stdErr = new PrintWriter(stdErrWriter);

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();

        // Compile
        JavaCompiler.CompilationTask task = compiler.getTask(stdOut, fileManager,
                diagnostics, Arrays.asList("-d",
                        (buildPath != null) ?  buildPath : "src/main/classes"),
                null, Collections.singleton(fileObject));

        boolean result = task.call();

        stdOut.close();
        stdErr.close();

        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
            appendStyledText(
                    textArea,
                    diagnostic.getMessage(null),
                    (diagnostic.getKind() == Diagnostic.Kind.ERROR) ? "red" : "black"
            );
        }

        return result;

    }

    private static String readSourceFile(File file) {

        StringBuilder sourceCode = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sourceCode.append(line).append("\n");
            }
        } catch (IOException e) {
            System.out.println(e.getMessage());
            return null;
        }
        return sourceCode.toString();

    }

    private static void appendStyledText(ConsoleTextArea textArea, String text, String color) {

        int start = textArea.getLength();
        textArea.appendText(text);
        int end = textArea.getLength();
        textArea.setStyle(start, end, "-fx-fill: " + color + ";");

    }

}
