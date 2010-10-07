import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.TreeMap;

public class Akin2 {
	//setting
	private Boolean debug = false;
	
	private String uName = "test";
    private String pass = "test";
    private Connection con;
    
	private ArrayList<Integer> probableID;
	private ArrayList<Integer> usedQuestions;
	private ArrayList<Integer> prevRM;
	public HashMap<Integer, Integer> questions;
	private HashMap<Integer, Integer> tally; //< userid, corrects > +1 for correct yes/no, -1 for wrong, 0 for anything else
	public int foundID;
	private int questionCount = 0;
	private int idkcount = 0;
	
	public Akin2() throws SQLException {
		con = DriverManager.getConnection("jdbc:mysql://localhost/akindb", uName, pass);
		probableID = new ArrayList<Integer>();
		usedQuestions = new ArrayList<Integer>();
		prevRM = new ArrayList<Integer>();
		tally = new HashMap<Integer, Integer>();
		questions = new HashMap<Integer, Integer>();
		
		addAllChar();
		
		Boolean b = false;
		while(!b) {
			b = question();
		}
		if(foundID != -1) printCharInfo();
	}

	private void addAllChar() throws SQLException {
		PreparedStatement stmt = con.prepareStatement("SELECT id FROM characters");
		ResultSet rs = stmt.executeQuery();
		while(rs.next()) {
			probableID.add(rs.getInt(1));
			tally.put(rs.getInt(1), 0);
		}
		rs.close();
		
	}

	private Boolean question() throws SQLException { //returns true if thinks it got char, false otherwise
		questionCount++;
		
		if(debug) cout("Before: "); printIDs();
		
		int qid = decideQID();
		if(qid == -1) {
			forceFind();
			return true;
		}
		
		int ans = askQuestion(qid);
		questions.put(qid, ans);
		filterIDs(qid, ans);
		
		autoBalance();
		
		int f = isFound();
		if(f != 0) {
			foundID = f;
			return true;
		}
		return false;
	}
	
	private int decideQID() throws SQLException { //return -1 if no questions and then triggers forceFind()
		//idea: chose the q that separates the list by the largest margin
		//TODO: Continue with this, but instead of finding most common, find the one with the most yes's and no's answered about it
		
		//add all the question ids of the probable chars and find the most commonly occurring id
		ArrayList<Integer> possibleQIDs = new ArrayList<Integer>();
		ResultSet rs;
		//get questions that each probable question have answered and find the most asked one that was not answered 0
		PreparedStatement stmt = con.prepareStatement("SELECT question_id FROM answers WHERE char_id = ? AND ans != 0");
		for(int charid : probableID) {
			stmt.setInt(1, charid);
			rs = stmt.executeQuery();
			rs.beforeFirst();
			while(rs.next()) {
				int toadd = rs.getInt(1);
				if(!usedQuestions.contains(toadd)) {
					possibleQIDs.add(toadd);
				}
			}
		}
		
		//find most commonly occuring
		TreeMap<Integer, Integer> occurences = new TreeMap<Integer, Integer>();
		for (int i : possibleQIDs) {
			if(occurences.containsKey(i)) {
				occurences.put(i, occurences.get(i)+1);
			} else {
				occurences.put(i, 1);
			}
		}
		
		int idm = -1;
		int idmval = -1;
		for (Map.Entry<Integer, Integer> entry : occurences.entrySet()) {
			if(entry.getValue() > idmval) {
				idmval = entry.getValue();
				idm = entry.getKey();
			}
		}
		
		usedQuestions.add(idm);
		return idm;
	}
	
	private int askQuestion(int qid) throws SQLException { //returns -1 for no, 1 for yes, 0 for everything else
		if(debug) cout("(QID: "+qid+")");
		
		PreparedStatement stmt = con.prepareStatement("SELECT question FROM questions WHERE id = ? LIMIT 1");
		stmt.setInt(1, qid);
		ResultSet rs = stmt.executeQuery();
		String q = "";
		if(rs.next()) {
			q = rs.getString(1);
		} else {
			return 0;
		}
		rs.close();
		
		String response = cin(q);
		if(response.toLowerCase().charAt(0) == 'y') return 1;
		else if(response.toLowerCase().charAt(0) == 'n') return -1;
		return 0;
	}
	
	private void filterIDs(int qid, int ans) throws SQLException { //modifies the arraylist of probable chars by checking questions
		
		//get charids that match the question wrong ans combination
		ArrayList<Integer> matches = new ArrayList<Integer>();
		int wrans;
		if(ans == 1) {
			wrans = -1;
		} else if(ans == -1) {
			wrans = 1;
		} else {
			idkcount++;
			wrans = -2; //will return nothing from db
		}
		
		//takes out of probableID those who answered the question completely wrong
		PreparedStatement stmt = con.prepareStatement("SELECT char_id FROM answers WHERE question_id = ? AND ans = ?");
		stmt.setInt(1, qid);
		stmt.setInt(2, wrans);
		ResultSet rs = stmt.executeQuery();
		rs.beforeFirst();
		while(rs.next()) {
			matches.add(rs.getInt(1));
			tally.put(rs.getInt(1), tally.get(rs.getInt(1))-1);
		}
		rs.close();
		if(ans != 0) probableID.removeAll(matches);
		
		//deals with tally
		stmt = con.prepareStatement("SELECT char_id FROM answers WHERE question_id = ? AND ans = ?");
		stmt.setInt(1, qid);
		stmt.setInt(2, ans);
		rs = stmt.executeQuery();
		while(rs.next()) {
			try {
				tally.put(rs.getInt(1), tally.get(rs.getInt(1))+1);
			} catch (Exception e) {
				e.printStackTrace();
				cout("ID"+rs.getInt(1));
				System.exit(1);
			}
		}
		rs.close();
	}
	
	private int isFound() { //returns 0 if not, char_id otherwise
		if(probableID.size() < 1) {
			cout("I Guess the person who doesn't know.");
			return -1;
		}
		
		//find the most likely value and check if it is at least 3 tallies higher than the next most likely
		if(questionCount-idkcount > 10) {
			int idm = -1;
			int idmval = -1;
			int idmdiff = -1;
			for (Map.Entry<Integer, Integer> entry : tally.entrySet()) {
				if(entry.getValue() > idmval && probableID.contains(idm)) {
					idmdiff = entry.getValue()-idmval;
					idmval = entry.getValue();
					idm = entry.getKey();
				}
			}
			if(idmdiff > 3) { //fix
				return idm;
			}
		}
		
		if(probableID.size() == 1) return probableID.get(0);
		return 0;
	}
	
	private void forceFind() {
		HashMap<Integer, Integer> idPoints = new HashMap<Integer, Integer>();
		for (int i : probableID) {
			if(tally.containsKey(i)) {
				idPoints.put(i, tally.get(i));
			}
		}
		
		int idm = -1;
		int idmval = -1;
		for (Map.Entry<Integer, Integer> entry : idPoints.entrySet()) {
			if(entry.getValue() > idmval) {
				idmval = entry.getValue();
				idm = entry.getKey();
			}
		}
		
		foundID = idm;
	}
	
	private void autoBalance() {
		if(questionCount-idkcount < 10) return; //not enough data
		
		ArrayList<Integer> torm = new ArrayList<Integer>();
		int minGoodForProb = (int)((questionCount-idkcount)/3); //minimum tally needed to be in probableIDs
		
		//remove from probableIDs that are not doing as well as minGoodForProb
		for(int charid : probableID) {
			if(tally.get(charid) == null || tally.get(charid) < minGoodForProb) {
				torm.add(charid);
				prevRM.add(charid);
			}
		}
		probableID.removeAll(torm);
		
		//add in tallys to probableIDs that are doing better than minforgoodprob only if haven't been removed before
		for (Map.Entry<Integer, Integer> entry : tally.entrySet()) {
			if(entry.getValue() >= minGoodForProb) {
				if(!probableID.contains(entry.getValue()) && !prevRM.contains(entry.getValue())) {
					probableID.add(entry.getValue());
				}
			}
		}
	}

	private void printCharInfo() throws SQLException {
		PreparedStatement stmt = con.prepareStatement("SELECT name FROM characters WHERE id = ? LIMIT 1");
		stmt.setInt(1, foundID);
		ResultSet rs = stmt.executeQuery();
		String q = null;
		if(rs.next()) {
			q = rs.getString(1);
		}
		
		rs.close();
		
		cout("I Guess: "+q);
		if(cin("Am I right?").toLowerCase().charAt(0) == 'n') {
			int newID = getCorrectCharacter();
			foundID = newID;
		}
	}
	
	private int getCorrectCharacter() throws SQLException {
		int char_id;
		
		HashMap<Integer, String> poss = new HashMap<Integer, String>();
		String inname = cin("Correct Character");
		PreparedStatement stmt = con.prepareStatement("SELECT id, name FROM characters WHERE name LIKE ?");
		stmt.setString(1, "%"+inname+"%");
		ResultSet rs = stmt.executeQuery();
		while(rs.next()) {
			poss.put(rs.getInt(1), rs.getString(2));
		}
		
		cout(poss.toString());
		int charnum = Integer.parseInt(cin("Type the number of the correct character (-1 for all wrong)"));
		if(charnum == -1) {
			char_id = addNewChar(inname);
		} else {
			char_id = charnum;
		}
		
		return char_id;
	}
	private int addNewChar(String name) throws SQLException {
		PreparedStatement stmt = con.prepareStatement("INSERT IGNORE INTO characters (name) VALUES (?)");
		stmt.setString(1, name);
		stmt.execute();
		stmt = con.prepareStatement("SELECT id FROM characters WHERE name=?");
		stmt.setString(1, name);
		ResultSet rs = stmt.executeQuery();
		rs.next();
		int id = rs.getInt(1);
		rs.close();
		return id;
	}
	
	public static void cout(String s) {
		System.out.println(s);
	}
	public static String cin(String q) {
		Scanner getin = new Scanner(System.in);
		System.out.print(q+" >> ");
		return getin.nextLine();
	}
	
	public void printIDs() { //debug
		if(debug) cout(probableID.toString());
	}
}
