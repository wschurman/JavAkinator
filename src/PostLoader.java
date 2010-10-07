import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;


public class PostLoader {
	
	//settings
	private int quality_control = 10; //add or subtract from quality depending on right or wrong answer
	private int new_quality = 20; //quality of a new answered question
	
	
	private String uName = "test";
    private String pass = "test";
    private Connection con;
	private HashMap<Integer, Integer> answers;
	private int char_id;
	
	public PostLoader(HashMap<Integer, Integer> answers, int char_id) throws SQLException {
		con = DriverManager.getConnection("jdbc:mysql://localhost/akindb", uName, pass);
		
		this.answers = answers;
		this.char_id = char_id;
		
		loadFromHash();
	}
	
	private void loadFromHash() throws SQLException {
		PreparedStatement stmt1 = con.prepareStatement("SELECT ans, quality FROM answers WHERE char_id = ? AND question_id = ? LIMIT 1");
		PreparedStatement stmt2 = con.prepareStatement("INSERT INTO answers (char_id, question_id, ans, quality) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE ans=?, quality=?");
		ResultSet rs;
		
		for (Map.Entry<Integer, Integer> entry : answers.entrySet()) {
			int qid = entry.getKey();
			int ans = entry.getValue();
			int stored_ans;
			int stored_qua;
			
			stmt1.setInt(1, char_id);
			stmt1.setInt(2, qid);
			rs = stmt1.executeQuery();
			if(rs.next()) { //already in table
				stored_ans = rs.getInt(1);
				stored_qua = rs.getInt(2);
				
				if(stored_ans == ans) { //increase quality
					stored_qua += quality_control;
				} else if(ans == 0) {
					//do nothing because nothing is known
				} else if(stored_ans != ans) {
					if(stored_qua < 11) { //need to switch
						stored_ans = ans;
						stored_qua = new_quality;
					} else { //just reduce quality
						stored_qua -= quality_control;
					}
				}
			} else { //not in table
				stored_ans = ans;
				stored_qua = new_quality;
			}
			rs.close();
			
			stmt2.setInt(1, char_id);
			stmt2.setInt(2, qid);
			stmt2.setInt(3, stored_ans);
			stmt2.setInt(4, stored_qua);
			stmt2.setInt(5, stored_ans);
			stmt2.setInt(6, stored_qua);
			stmt2.addBatch();	
		}
		
		stmt2.executeBatch();
		stmt2.close();
	}
}
