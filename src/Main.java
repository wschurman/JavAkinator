import java.sql.SQLException;
import java.util.HashMap;


public class Main {
	private static int char_id;
	private static HashMap<Integer, Integer> answers;
	
	public static void main(String[] args) throws ClassNotFoundException, SQLException {
		//new Loader("data.txt");
		answers = new HashMap<Integer, Integer>();
		
		Akin2 ak = new Akin2();
		char_id = ak.foundID;
		if(char_id != -1) {
			answers = ak.questions;
			new PostLoader(answers, char_id);
		}
	}
}
