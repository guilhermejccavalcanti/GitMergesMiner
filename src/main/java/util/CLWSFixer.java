package util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CLWSFixer {

	public static void countConsecutiveLinesConflictsByMergeScenarios(){
		Map<String,String> oldEntries = readLog("numbers-scenarios-fix.csv");
		Map<String,String> upEntries  = readLog("numbers-scenarios-cl.csv");
		StringBuilder builder = new StringBuilder();
		for(String key : upEntries.keySet()){
			String[] columns = upEntries.get(key).split(";");
			int conLines = Integer.parseInt(columns[2]);
			//int S3Mconfs = Integer.parseInt(columns[13]);
			int S3Mconfs = Integer.parseInt((oldEntries.get(key)).split(";")[13]);

			int S3MconfsUpdated = S3Mconfs - conLines;
			String oldResult = oldEntries.get(key);
			oldResult = oldResult + ";" + ((S3MconfsUpdated > 0) ? S3MconfsUpdated : 0);
			builder.append(oldResult).append("\n");
		}
		printUpdatedEntries(builder.toString(),"numbers-scenarios-fix-cl.csv");
	}

	public static void countConsecutiveLinesConflictsByMergedFiles(){
		//TODO
	}

	public static void countWhiteSpaceConflictsByMergeScenarios(){
		Map<String,String> oldEntries = readLog("numbers-scenarios-fix.csv");
		Map<String,String> upEntries  = readLog("numbers-scenarios-ws.csv");
		StringBuilder builder = new StringBuilder();
		for(String key : upEntries.keySet()){
			//			String[] columns = upEntries.get(key).split(";");
			//			int whiteSpace = Integer.parseInt(columns[13]);
			//			int S3MconfsUpdated = whiteSpace;
			//			String oldResult = oldEntries.get(key);
			//			oldResult = oldResult + ";" +S3MconfsUpdated;
			//			builder.append(oldResult).append("\n");
			try{
				String[] columns = upEntries.get(key).split(";");
				int S3Mconfsup = Integer.parseInt(columns[13]);
				int S3Mconfs = Integer.parseInt((oldEntries.get(key)).split(";")[13]);
				int whiteSpace = S3Mconfs - S3Mconfsup;

				int S3MconfsUpdated = S3Mconfs - ((whiteSpace>0)?whiteSpace:0);
				String oldResult = oldEntries.get(key);
				oldResult = oldResult + ";" + ((S3MconfsUpdated > 0) ? S3MconfsUpdated : 0);
				builder.append(oldResult).append("\n");
			}catch(Exception e){
				continue;
			}
		}
		printUpdatedEntries(builder.toString(),"numbers-scenarios-fix-ws.csv");
	}

	public static void countWhiteSpaceConflictsByMergedFiles(){
	}

	private static void printUpdatedEntries(String str, String path) {
		try(BufferedWriter writer = new BufferedWriter(new FileWriter(path))){
			writer.write(str);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static Map<String, String> readLog(String path) {
		Map<String,String> entries = new LinkedHashMap<String,String>();
		try(BufferedReader reader = Files.newBufferedReader(Paths.get(path))){
			List<Object> results = reader.lines().collect(Collectors.toList());
			entries = new LinkedHashMap<String,String>(results.size()-1);
			for(int i = 1; i < results.size(); i++){
				String[] columns = ((String)results.get(i)).split(";");
				String key = columns[0]+";"+columns[1];
				String value = String.join(";", columns);
				entries.put(key, value);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return entries;
	}

	public static void main(String[] args) {
		//countConsecutiveLinesConflictsByMergeScenarios();
		countWhiteSpaceConflictsByMergeScenarios();
	}
}
