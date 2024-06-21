import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

public class CrptApi {

    private final int requestLimit;
    private final long timeUnitMillis;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final AtomicInteger requestCount;
    private final ReentrantLock lock;
    private final ScheduledExecutorService scheduler;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.requestLimit = requestLimit;
        this.timeUnitMillis = timeUnit.toMillis(1);
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
        this.requestCount = new AtomicInteger(0);
        this.lock = new ReentrantLock();
        this.scheduler = Executors.newScheduledThreadPool(1);

        // Scheduler to reset the request count after each time unit
        scheduler.scheduleAtFixedRate(() -> requestCount.set(0), 0, this.timeUnitMillis, TimeUnit.MILLISECONDS);
    }

    public void createDocument(Document document, String signature) throws IOException, InterruptedException {
        // Ensure that the request limit is not exceeded
        lock.lock();
        try {
            while (requestCount.get() >= requestLimit) {
                lock.unlock();
                Thread.sleep(100); // Wait before retrying
                lock.lock();
            }
            requestCount.incrementAndGet();
        } finally {
            lock.unlock();
        }

        // Prepare the request body
        String json = objectMapper.writeValueAsString(document);

        // Create the HTTP request
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://ismp.crpt.ru/api/v3/1k/documents/create"))
                .header("Content-Type", "application/json")
                .header("Signature", signature)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        // Send the request
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("Failed to create document: " + response.body());
        }
    }

    public static class Document {
        public Description description;
        public String doc_id;
        public String doc_status;
        public String doc_type;
        public boolean importRequest;
        public String owner_inn;
        public String participant_inn;
        public String producer_inn;
        public String production_date;
        public String production_type;
        public List<Product> products;
        public String reg_date;
        public String reg_number;

        public static class Description {
            public String participantInn;
        }

        public static class Product {
            public String certificate_document;
            public String certificate_document_date;
            public String certificate_document_number;
            public String owner_inn;
            public String producer_inn;
            public String production_date;
            public String tnved_code;
            public String uit_code;
            public String uitu_code;
        }
    }

    private static void printUsage() {
        System.out.println("Usage: java CrptApi <inputFile> <outputFile> <criterion> [wordIndex]");
        System.out.println("Criterion:");
        System.out.println("  1 - Alphabetically");
        System.out.println("  2 - By line length");
        System.out.println("  3 - By specific word in the line");
    }

    public static void main(String[] args) {
        try {
            CrptApi api = new CrptApi(TimeUnit.MINUTES, 10);

            // Create a sample document
            Document doc = new Document();
            doc.description = new Document.Description();
            doc.description.participantInn = "string";
            doc.doc_id = "string";
            doc.doc_status = "string";
            doc.doc_type = "LP_INTRODUCE_GOODS";
            doc.importRequest = true;
            doc.owner_inn = "string";
            doc.participant_inn = "string";
            doc.producer_inn = "string";
            doc.production_date = "2020-01-23";
            doc.production_type = "string";
            doc.products = List.of(new Document.Product());
            doc.reg_date = "2020-01-23";
            doc.reg_number = "string";

            String signature = "sample_signature";
            api.createDocument(doc, signature);
            System.out.println("Document created successfully.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}