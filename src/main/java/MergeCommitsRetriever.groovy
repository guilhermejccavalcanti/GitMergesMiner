import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat


public class MergeCommitsRetriever {

	String clonePath
	String sinceDate //dd/mm/aaaa
	String untilDate //dd/mm/aaaa


	public MergeCommitsRetriever(String clonePath, String sincedate, String untildate){
		this.clonePath = clonePath
		this.sinceDate = sincedate
		this.untilDate = untildate
	}

	public ProcessBuilder getProcessBuilder(){
		ProcessBuilder result = null
		if(!this.sinceDate.equals('') && !this.untilDate.equals('')){
			this.sinceDate = '--since=\"' + this.sinceDate + '\"'
			this.untilDate = '--until=\"' + this.untilDate + '\"'
			result = new ProcessBuilder("git", "log", "--merges", this.sinceDate, this.untilDate)
		} else  if(!this.untilDate.equals('')){
			this.untilDate = '--until=\"' + this.untilDate + '\"'
			result = new ProcessBuilder("git", "log", "--merges", this.untilDate)
		} else if(!this.sinceDate.equals('') && !this.untilDate.equals('')){
			this.sinceDate = '--since=\"' + this.sinceDate + '\"'
			result = new ProcessBuilder("git", "log", "--merges", this.sinceDate)
		} else { 
			result = new ProcessBuilder("git", "log", "--merges")
		}
		return result
	}

	public ArrayList<MergeCommit> retrieveMergeCommits(){
		ArrayList<MergeCommit> merges = new ArrayList<MergeCommit>()

		try{
			ProcessBuilder pb = this.getProcessBuilder()
			pb.directory(new File(this.clonePath))
			Process p = pb.start()

			BufferedReader buf = new BufferedReader(new InputStreamReader(p.getInputStream()))
			String line = ""

			while ((line=buf.readLine())!=null) {
				if(line.startsWith('commit')){
					MergeCommit merge = new MergeCommit()
					merge.sha = line.split(' ')[1]
					line=buf.readLine()
					String[] data = line.split(' ')
					merge.parent1 = data[1]
					merge.parent2 = data[2]
					line=buf.readLine()
					line=buf.readLine()
					Date date = this.getCommitDate(line)
					merge.date = date
					merges.add(merge)
				}
			}
			p.getInputStream().close()
			Collections.reverse(merges)
		}catch(Exception e){
			e.printStackTrace()
		}
		return merges
	}

	public Date getCommitDate(String d){
		SimpleDateFormat formatter = new SimpleDateFormat("dd/MMM/yyyy", Locale.UK)
		Date result = null
		String[] data = d.split(' ')
		String day = data[5]
		String month = data[4]
		String year = data[7]
		String dateInString = day + '/' + month + '/' + year
		result = formatter.parse(dateInString)
		return result
	}

	public static void main(String[] args){
		/*date is optional, if you want to get all commits pass the date parameter as an empty string
		 * otherwise pass the date parameter as an string with the format "yyyy-MM-dd" */
		MergeCommitsRetriever merges = new MergeCommitsRetriever("C:\\Mestrado\\FPFNAnalysis\\projects\\lucene-solr\\git", "")
		ArrayList<MergeCommit> m = merges.retrieveMergeCommits()
		for(MergeCommit mc : m){
			println m.size()
			println mc.toString();
		}
	}
}
