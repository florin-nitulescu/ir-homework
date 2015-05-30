import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Date;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.ro.RomanianAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.LongField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.search.spell.Dictionary;
import org.apache.lucene.search.spell.LuceneDictionary;
import org.apache.lucene.search.spell.SpellChecker;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;


public class IRIndexer {

	@SuppressWarnings("deprecation")
	public static void main(String[] args) {

		String docsPath = "E:\\ir-doc-collection";
		final Path docDir = Paths.get(docsPath);
		
		final Path indexPath = docDir.getParent().resolve("ir-index-dir");
		final Path spellIndexPath = docDir.getParent().resolve("ir-spell-index-dir");

		if (!Files.isReadable(docDir)) {
			System.out.println("Directorul ce contine colectia de documente (" + docDir.toAbsolutePath() + ") nu exista sau nu poate fi citit.");
		}
		
		Date start = new Date();
		Directory dir = null;
		try {
			System.out.println("Indexul va fi pastrat in directorul: " + indexPath.toAbsolutePath());
			
			dir = FSDirectory.open(indexPath.toFile());
			Analyzer analyzer = new RomanianAnalyzer();
			IndexWriterConfig iwc = new IndexWriterConfig(Version.LATEST, analyzer);
			
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
			
			IndexReader indexReader = null;
			Directory spellIndexDirectory = null;
			try {
				indexReader = IndexReader.open(dir);
				spellIndexDirectory = FSDirectory.open(spellIndexPath.toFile());
				
				Dictionary dictionary = new LuceneDictionary(indexReader, "contents");
				try (SpellChecker spellChecker = new SpellChecker(spellIndexDirectory)) {

					IndexWriterConfig spellIwc = new IndexWriterConfig(Version.LATEST, analyzer);
					spellChecker.indexDictionary(dictionary, spellIwc, true);
				}
			} finally {
				if (indexReader != null) {
					indexReader.close();
				}
			}
			
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
