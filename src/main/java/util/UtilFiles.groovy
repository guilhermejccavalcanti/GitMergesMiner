package util

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;

/**
 *This class is responsible for a specific manipulation of the revision files. Given a full list of revision files before the merges, it looks only for the revisions that still exists after the merges.
 * @author Guilherme
 *
 */
class UtilFiles {
	static void main (String[] args){
		ArrayList<String> acc = new ArrayList<String>()
		FileInputStream stream = new FileInputStream("C:\\Users\\Guilherme\\Desktop\\all-revisions.txt");
		InputStreamReader reader = new InputStreamReader(stream);
		BufferedReader br = new BufferedReader(reader);
		String linha = br.readLine();
		while(linha != null){
			File f = new File(linha)
			if(f.exists()){
				String parent = f.getParent() + File.separator;
				String destiny = parent.replaceAll("FSTMerge", "final")
				new AntBuilder().copy(todir:destiny) {fileset(dir:parent , defaultExcludes: false){}}
				acc.add(linha)
			}
			linha = br.readLine();
		}

		def out = new File('saida.txt')
		acc.each {
			out.append it
			out.append '\n'
		}
	}
}
