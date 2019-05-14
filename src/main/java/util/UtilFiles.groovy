package util

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import org.apache.commons.collections.CollectionUtils;
/**
 *This class is responsible for a specific manipulation of the revision files. Given a full list of revision files before the merges, it looks only for the revisions that still exists after the merges.
 * @author Guilherme
 *
 */
class UtilFiles {
	static void main (String[] args){
		
		List<String> oldKeys = Arrays.asList("key0","key1","key2","key5");
		List<String> newKeys = Arrays.asList("key0","key2","key5", "key6");
		List<String> list = new ArrayList<>(CollectionUtils.disjunction(newKeys, oldKeys));
		println list
		
		Map<String,FileEntry> conflictingFilesS3M = new HashMap<>();
		Map<String,FileEntry> conflictingFilesJDIME = new HashMap<>();

		new File('numbers-files.csv').eachLine {line ->
			try{
				String[] columns = line.split(';')
				String project = columns[0]
				String mergecommit = columns[1]
				String file = columns[5]
				int ssmergeconf = Integer.valueOf(columns[17])
				int jdimeconf = Integer.valueOf(columns[19])

				if(ssmergeconf > 0){
					FileEntry fes3m = conflictingFilesS3M.get(project+mergecommit);
					if(fes3m == null){
						fes3m = new FileEntry(project,mergecommit);
						fes3m.files.add(file);
						conflictingFilesS3M.put(project+mergecommit, fes3m);
					} else {
						fes3m.files.add(file);
					}
				}
				if(jdimeconf > 0){
					FileEntry fesjdime = conflictingFilesJDIME.get(project+mergecommit);
					if(fesjdime == null){
						fesjdime = new FileEntry(project+mergecommit,mergecommit);
						fesjdime.files.add(file);
						conflictingFilesJDIME.put(mergecommit, fesjdime);
					} else {
						fesjdime.files.add(file);
					}
				}
			}catch(Exception e){
				e.printStackTrace()
			}
		}
		
		for(String key : conflictingFilesS3M.keySet()){
			FileEntry fes3m = conflictingFilesS3M.get(key);
			FileEntry fesjdime = conflictingFilesJDIME.get(key);
			if(fesjdime != null){
				List<String> diffFiles = new ArrayList<>(CollectionUtils.disjunction(fes3m.files, fesjdime.files));
				if(!diffFiles.isEmpty()){
					println key
				}
			}
		}
		
		for(String key : conflictingFilesJDIME.keySet()){
			FileEntry fes3m = conflictingFilesS3M.get(key);
			FileEntry fesjdime = conflictingFilesJDIME.get(key);
			if(fes3m != null){
				List<String> diffFiles = new ArrayList<>(CollectionUtils.disjunction(fes3m.files, fesjdime.files));
				if(!diffFiles.isEmpty()){
					println key
				}
			}
		}
		
		println 'fim'
	}
}

class FileEntry {
	String projectName;
	String mergeCommit;
	List<String> files = new ArrayList<>();

	public FileEntry(String p, String m){
		this.projectName = p;
		this.mergeCommit = m;
	}
}

