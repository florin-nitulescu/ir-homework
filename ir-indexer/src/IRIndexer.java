import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Date;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.LongField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;


public class IRIndexer {

	public static void main(String[] args) {

		String docsPath = "E:\\ir-doc-collection";
		final Path docDir = Paths.get(docsPath);
		
		final Path indexPath = docDir.getParent().resolve("ir-index-dir");

		if (!Files.isReadable(docDir)) {
			System.out.println("Directorul ce contine colectia de documente (" + docDir.toAbsolutePath() + ") nu exista sau nu poate fi citit.");
		}
		
		Date start = new Date();
		try {
			System.out.println("Indexul va fi pastrat in directorul: " + indexPath.toAbsolutePath());
			
			Directory dir = FSDirectory.open(indexPath);
			Analyzer analyzer = new StandardAnalyzer();
			IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
			
			iwc.setOpenMode(OpenMode.CREATE);
			
			final IndexWriter writer = new IndexWriter(dir, iwc);
			
			if (Files.isDirectory(docDir)) {
				Files.walkFileTree(docDir, new SimpleFileVisitor<Path>() {
					@Override
					public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {

						long modifiedTime = attributes.lastModifiedTime().toMillis();
						indexDocument(writer, file, modifiedTime);
						
						return FileVisitResult.CONTINUE;
					}
				});
			} else {
				indexDocument(writer, docDir, Files.getLastModifiedTime(docDir).toMillis());
			}
			
			writer.close();
			
			Date end = new Date();
			System.out.println("Total milisecunde: " + (end.getTime() - start.getTime()));
						
		} catch (IOException e) {
			System.out.println("caught a " + e.getClass() + " \n with message: " + e.getMessage());
		}
	
		//System.out.println(docDir.getParent().toString());

	}

	private static void indexDocument(final IndexWriter writer, Path file, long modifiedTime) throws IOException {
		try (InputStream stream = Files.newInputStream(file)) {
			
			Document doc = new Document();
			
			Field pathField = new StringField("path", file.toString(), Field.Store.YES);
			doc.add(pathField);
			doc.add(new TextField("contents", new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))));
			doc.add(new LongField("modified", modifiedTime, Field.Store.NO));
			
	        System.out.println("adding " + file);
	        writer.addDocument(doc);
		}
	}

}
