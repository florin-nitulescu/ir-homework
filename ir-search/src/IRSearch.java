import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.Vector;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.ro.RomanianAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.DocsEnum;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.MultiFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.spell.SpellChecker;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

public class IRSearch {
	
	static private class QuerySuggester extends QueryParser {
		private boolean suggestedQuery = false;
		private Directory spellIndexDirectory = null;
		private ArrayList<Term> _QueryTerms;
		
		public QuerySuggester(String field, Analyzer analyzer, Directory spellIndexDirectory) {
			super(field, analyzer);
			this.spellIndexDirectory = spellIndexDirectory;
		}

		@Override
		protected Query getFieldQuery(String field, String queryText, boolean quoted)
				throws ParseException {
			// Copied from org.apache.lucene.queryParser.QueryParser
			// replacing construction of TermQuery with call to getTermQuery()
			// which finds close matches.
			StringReader stringReader = new StringReader(queryText);
		    TokenStream tokenStream;
			Vector<String> v = new Vector<String>();
			_QueryTerms = new ArrayList<Term>();
			try {
				tokenStream = getAnalyzer().tokenStream(field, stringReader);
			    CharTermAttribute termAttribute = tokenStream.addAttribute(CharTermAttribute.class);

				String t;

				tokenStream.reset();
				
				while (tokenStream.incrementToken()) {
					t = termAttribute.toString();
					if (t == null)
						break;
					v.addElement(t);
				}
				
				tokenStream.close();
			
				System.out.println("Termenii din query: " + String.join(", ", v));
	
				if (v.size() == 0)
					return null;
				else if (v.size() == 1) {
					Term term = getTerm(field, v.elementAt(0));
					_QueryTerms.add(term);
					return new TermQuery(term);
				} else {
					PhraseQuery q = new PhraseQuery();
					q.setSlop(getPhraseSlop());
					for (int i = 0; i < v.size(); i++) {
						Term term = getTerm(field, v.elementAt(0));
						_QueryTerms.add(term);
						q.add(term);
					}
					return q;
				}
			} catch (IOException e1) {
				return null;
			}
		}

		
		public ArrayList<Term> getQueryTerms() {
			return _QueryTerms;
		}


		private Term getTerm(String field, String queryText) throws ParseException, IOException {
			SpellChecker spellChecker = new SpellChecker(spellIndexDirectory);
			try {
				if (spellChecker.exist(queryText)) {
					return new Term(field, queryText);
				}
				String[] similarWords = spellChecker.suggestSimilar(queryText, 1);
				if (similarWords.length == 0) {
					return new Term(field, queryText);
				}			
				suggestedQuery = true;
				return new Term(field, similarWords[0]);
			} catch (IOException e) {
				throw new ParseException(e.getMessage());
			} finally {
				spellChecker.close();
			}
		}		
		
		public boolean hasSuggestedQuery() {
			return suggestedQuery;
		}
		
	}
	
	public static void main(String[] args) throws Exception {
		String indexPath = "E:\\ir-index-dir";
		
		IndexReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(indexPath).toFile()));
		IndexSearcher searcher = new IndexSearcher(reader);
		Analyzer analyzer = new RomanianAnalyzer();
		
		BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
		
		
		Directory spellIndexDirectory = FSDirectory.open(Paths.get("E:\\ir-spell-index-dir").toFile());
		QuerySuggester suggester = new QuerySuggester("contents", analyzer, spellIndexDirectory);
		suggester.setDefaultOperator(QueryParser.AND_OPERATOR);
		
		while (true) {
			System.out.print("Interogare: ");
			String line = in.readLine();
			
			if (line == null || line.length() == -1) {
				break;
			}
			
			line = line.trim();
			if (line.length() == 0) {
				break;
			}
			

			Query query = suggester.parse(line);
			
			if (suggester.hasSuggestedQuery()) {

				System.out.println("Query cu sugestie!");
			}
			System.out.println("Se cauta dupa: " + query.toString("contents"));
			
			Date start = new Date();
			TopDocs results = searcher.search(query, 100000);
			Date end = new Date();
			
			System.out.println("Timp cautare: " + (end.getTime() - start.getTime()) + "ms");
			
			ScoreDoc[] hits = results.scoreDocs;
			int numTotalHits = results.totalHits;
			
			
			System.out.println("Nr. de rezultate returnate: " + numTotalHits);
			
			
			for (int i = 0; i < hits.length; i++) {
				Document doc = searcher.doc(hits[i].doc);
				String path = doc.get("path");
				
				
				System.out.println("Explain: " + searcher.explain(query, hits[i].doc).toString());
				
				for (Term term : suggester.getQueryTerms()) {
				
					DocsEnum docsEnum = MultiFields.getTermDocsEnum(reader, MultiFields.getLiveDocs(reader), term.field(), term.bytes());
					if (docsEnum != null) {
						while (docsEnum.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
					    	if (docsEnum.docID() == hits[i].doc) {
					    		double tf = (Math.log(1 + docsEnum.freq()) / Math.log(2));
					        	System.out.println("Term freq: " + tf);
				    		}
				        }
					}
					
					double idf = Math.log((double)reader.numDocs() / reader.docFreq(term)) / Math.log(2);
					
					System.out.println("Inverse doc. freq: " + idf);
				}
				
				if (path != null) {
					System.out.println((i + 1) + ". Scor: " + hits[i].score + "\n    Cale: " + path);
					String title = doc.get("title");
					if (title != null) {
						System.out.println("  Titlu: " + title);
					}
				} else {
					System.out.println((i + 1) + ". Scor: " + hits[i].score + "\n    Cale: Document fara cale");
				}
			}
		}
		
		System.out.println("Gata.");
		
	}

}
