package de.peeeq.wurstio.languageserver2;

import de.peeeq.wurstio.Main;
import de.peeeq.wurstscript.WLogger;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.FileHandler;
import java.util.logging.SimpleFormatter;

/**
 *
 */
public class WurstLanguageServer implements org.eclipse.lsp4j.services.LanguageServer, LanguageClientAware {
    private String rootUri;
    private de.peeeq.wurstio.languageserver2.LanguageWorker languageWorker = new de.peeeq.wurstio.languageserver2.LanguageWorker();

    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
        System.err.println("initializing ");
        setupLogger();
        WLogger.info("initialize " + params.getRootUri());
        rootUri = params.getRootUri();
        languageWorker.setRootPath(rootUri);

        ServerCapabilities capabilities = new ServerCapabilities();
        capabilities.setCompletionProvider(new CompletionOptions(false, Collections.singletonList(".")));
        capabilities.setHoverProvider(true);
        capabilities.setDefinitionProvider(true);
        capabilities.setSignatureHelpProvider(new SignatureHelpOptions(Arrays.asList("(", ".")));
        capabilities.setDocumentHighlightProvider(true);
        capabilities.setReferencesProvider(true);
        capabilities.setExecuteCommandProvider(new ExecuteCommandOptions(WurstCommands.providedCommands()));

        capabilities.setTextDocumentSync(Either.forLeft(TextDocumentSyncKind.Full));


        InitializeResult res = new InitializeResult(capabilities);
        WLogger.info("initialization done: " + params.getRootUri());
        return CompletableFuture.completedFuture(res);
    }



    private void setupLogger()  {
        Main.setUpFileLogging("wurst_langserver");
        System.setProperty("java.util.logging.SimpleFormatter.format", "%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS %4$-6s %2$s %5$s%6$s%n");

        try {
            FileHandler handler = new FileHandler("%t/wurst/wurst_langserver%g.log", Integer.MAX_VALUE, 20);
            handler.setFormatter(new SimpleFormatter());
            WLogger.setHandler(handler);
        } catch (IOException e) {
            throw new RuntimeException("Could not setup logging!");
        }
    }



    @Override
    public CompletableFuture<Object> shutdown() {
        WLogger.info("shutdown");
        languageWorker.stop();
        return CompletableFuture.completedFuture("ok");
    }


    @Override
    public void exit() {
        System.exit(0);
    }

    @Override
    public TextDocumentService getTextDocumentService() {
        WLogger.info("getTextDocumentService");
        return new WurstTextDocumentService(languageWorker);
    }

    @Override
    public WorkspaceService getWorkspaceService() {
        WLogger.info("getWorkspaceService");
        return new WurstWorkspaceService(this);
    }


    @Override
    public void connect(LanguageClient client) {
        WLogger.info("connect to LanguageClient");
        languageWorker.setLanguageClient(client);
    }

    public LanguageWorker worker() {
        return languageWorker;
    }

    public String getRootUri() {
        return rootUri;
    }
}