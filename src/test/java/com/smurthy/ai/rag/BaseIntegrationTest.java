package com.smurthy.ai.rag;

import com.smurthy.ai.rag.service.TemporalQueryService;
import com.smurthy.ai.rag.service.provider.CompositeStockQuoteProvider;
import com.smurthy.ai.rag.service.provider.FinnhubProvider;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.util.FileSystemUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test", "data-ingestion"}) // Activate both profiles
@TestPropertySource(properties = { "finance.api.enabled=true" })
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class BaseIntegrationTest {

    @Autowired
    protected VectorStore vectorStore;

    @MockBean
    protected TemporalQueryService temporalQueryService;

    @MockBean
    protected FinnhubProvider finnhubProvider;

    @MockBean
    protected CompositeStockQuoteProvider compositeStockQuoteProvider;

    @Value("${rag.documents.watch-dir}")
    private String watchDir;
    private Path watchDirPath;

    private static final AtomicBoolean FIXTURES_LOADED = new AtomicBoolean(false);

    protected static final Document MUNICIPAL_BOND_DOC = new Document(
        "Municipal-to-Treasury Yield Ratios are percentages that compare the yields on " +
        "municipal bonds to those on U.S. Treasury bonds of similar maturities. This ratio " +
        "is a key metric for assessing the relative value and potential return of municipal bonds. " +
        "The 10-year ratio is typically around 80-90%, while the 30-year ratio is around 85-95%. " +
        "These ratios help investors evaluate the tax-equivalent yield and overall attractiveness " +
        "of municipal bonds compared to taxable Treasury securities.",
        Map.of("source_filename", "test-municipal-bonds.txt", "test_fixture", "true", "category", "bonds")
    );

    protected static final Document BOND_TRADING_DOC = new Document(
        "Corporate bond trading involves the buying and selling of debt securities issued by corporations. " +
        "Bond traders must consider factors such as credit ratings, maturity dates, and coupon rates. " +
        "The secondary bond market provides liquidity for investors who wish to sell bonds before maturity.",
        Map.of("source_filename", "test-bond-trading.txt", "test_fixture", "true", "category", "bonds")
    );

    @BeforeAll
    public void setupSharedFixtures() throws IOException {
        this.watchDirPath = Path.of(watchDir);
        if (!FIXTURES_LOADED.get()) {
            synchronized (BaseIntegrationTest.class) {
                if (!FIXTURES_LOADED.get()) {
                    System.err.println("=== Setting up file-based test fixtures ===");
                    // Clean and create the watch directory
                    FileSystemUtils.deleteRecursively(watchDirPath);
                    Files.createDirectories(watchDirPath);

                    // Write documents to the watch directory to trigger ingestion
                    writeDocumentToWatchDir(MUNICIPAL_BOND_DOC);
                    writeDocumentToWatchDir(BOND_TRADING_DOC);

                    // Wait for the documents to be ingested by the file watcher
                    System.err.println("Waiting for shared fixtures to be ingested...");
                    Awaitility.await()
                        .atMost(Duration.ofSeconds(30))
                        .pollInterval(Duration.ofSeconds(2))
                        .until(() -> !vectorStore.similaritySearch(SearchRequest.builder().query("municipal bonds").topK(1).build()).isEmpty() &&
                                !vectorStore.similaritySearch(SearchRequest.builder().query("bond trading").topK(1).build()).isEmpty());

                    FIXTURES_LOADED.set(true);
                    System.err.println("=== Shared fixtures ingested successfully ===");
                }
            }
        }
    }

    private void writeDocumentToWatchDir(Document document) throws IOException {
        String fileName = (String) document.getMetadata().get("source_filename");
        if (fileName == null) {
            throw new IllegalArgumentException("Document must have 'source_filename' metadata.");
        }
        Path filePath = this.watchDirPath.resolve(fileName);
        Files.writeString(filePath, document.getText(), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
    }

    protected void addTestDocument(Document document) throws IOException {
        writeDocumentToWatchDir(document);
        // Wait for the specific document to be ingested
        String query = (String) document.getMetadata().get("source_filename");
        Awaitility.await()
            .atMost(Duration.ofSeconds(15))
            .pollInterval(Duration.ofSeconds(1))
            .until(() -> !vectorStore.similaritySearch(SearchRequest.builder().query(query).topK(1).build()).isEmpty());
    }

    protected void deleteTestDocument(Document document) {
        String fileName = (String) document.getMetadata().get("source_filename");
        // Find the document in the vector store by its source to get the ID
        List<Document> results = vectorStore.similaritySearch(SearchRequest.builder().query(fileName).topK(1).build());
        if (!results.isEmpty()) {
            try {
                vectorStore.delete(results.stream().map(Document::getId).toList());
                // Also delete the source file
                Files.deleteIfExists(this.watchDirPath.resolve(fileName));
            } catch (Exception e) {
                System.err.println("Warning: Failed to delete test document: " + e.getMessage());
            }
        }
    }

    protected void addTestDocumentsBatch(List<Document> documents) throws IOException {
        for (Document doc : documents) {
            addTestDocument(doc);
        }
    }
}
