/* Loader.java
 * Loads an exported akinator .txt file into MySQL database for Akinator.
 */

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Scanner;

public class Loader {
	private String uName = "test";
    private String pass = "test";
    private Connection con;
    private PreparedStatement astmt;
	
	public Loader(String file) throws ClassNotFoundException, SQLException {
		con = DriverManager.getConnection("jdbc:mysql://localhost/akindb", uName, pass);
		
		loadFromFile(file);
		
	}
	
	public int processCharacter(String cname) throws SQLException {
		int id = 0;
		PreparedStatement s = con.prepareStatement("INSERT IGNORE INTO characters (name) VALUES (?)");
		ResultSet rs;
		
		s.setString(1, cname);
		s.execute();
		
		s = con.prepareStatement("SELECT id FROM characters WHERE name LIKE ?");
		s.setString(1, cname);
		rs = s.executeQuery();
		
		rs.beforeFirst();
		if(rs.next()) {
			id = rs.getInt(1);
		}
		return id;
	}
	
	public int processQuestion(String q) throws SQLException {
		int id = 0;
		PreparedStatement s = con.prepareStatement("INSERT IGNORE INTO questions (question) VALUES (?)");
		ResultSet rs;
		
		s.setString(1, q);
		s.execute();
		
		s = con.prepareStatement("SELECT id FROM questions WHERE question LIKE ?");
		s.setString(1, q);
		rs = s.executeQuery();
		
		rs.beforeFirst();
		if(rs.next()) {
			id = rs.getInt(1);
		}
		return id;
	}
	
	public void loadFromFile(String file) throws SQLException {	
		System.out.println("Loading "+file+"...");
		int num = 0;
		int char_id;
		
		astmt = con.prepareStatement("INSERT IGNORE INTO answers (char_id, question_id, ans, quality) VALUES (?, ?, ?, 75)");
		
		try {
	        BufferedReader in = new BufferedReader(new FileReader(file));
	        String str;
	        String cname = in.readLine();
	        char_id = processCharacter(cname);
	        
	        while ((str = in.readLine()) != null) {
	            process(str, char_id);
	            num++;
	        }
	        in.close();
	    } catch (IOException e) { //no file
	    	System.out.println("No such file you stupid"+e+".");
	    }
		
	    astmt.executeBatch();
	    
	    System.out.println("Done processing "+num+" items.");
	}
	
	private void process(String ln, int char_id) throws SQLException
	{
		//format of raw data.
		//Quesytion?	YourAnswer	ExpectedAnswer
		String[] props = ln.split("\t");
		
		int qid = processQuestion(props[0]);
		int ans;
		if(props[2].equalsIgnoreCase("No")) ans = -1;
		else if(props[2].equalsIgnoreCase("Yes")) ans = 1;
		else ans = 0;
		
		astmt.setInt(1, char_id);
		astmt.setInt(2, qid);
		astmt.setInt(3, ans);
		astmt.addBatch();
		
	}
	
	public static void cout(String s) {
		System.out.println(s);
	}
	public static String cin(String q) {
		Scanner getin = new Scanner(System.in);
		System.out.print(q+" >> ");
		return getin.nextLine();
	}
}
