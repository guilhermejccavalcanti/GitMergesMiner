package util;

import java.util.Optional;

import fr.inria.jtravis.JTravis;
import fr.inria.jtravis.entities.Build;
import fr.inria.jtravis.entities.Builds;
import fr.inria.jtravis.entities.Repository;


public class TravisFinder {
	private static JTravis jTravis =  JTravis.builder().build();

	public static String findStatus(String sha, String project) throws InterruptedException{
		//1. get correponding build of the new merge commit		
		Optional<Repository> repository = jTravis.repository().fromSlug(project);
		Build build = null;
		int attempts = 0;
		if (repository.isPresent()) {
			Optional<Builds> optionalBuilds = jTravis.build().fromRepository(repository.get());
			if (optionalBuilds.isPresent()) {
				while(build == null && attempts < 10){
					for (Build b : optionalBuilds.get().getBuilds()) {
						if(b.getCommit().getSha().equals(sha)){
							build = b;
							break;
						}
					}
					if(build==null){
						attempts++;
						Thread.sleep(30000);
					}
				}
			}
		}
		//2. get new build status
		String status = "started";
		attempts = 0;
		if(build != null){
			while(status.equals("started") && attempts < 10){
				status = build.getState().name();
				if(status.equals("started")) {
					attempts++;
					Thread.sleep(30000);
				}
			}
		} else {
			status = "not found";
		}
		return status;
	}

	public static void main(String[] args) {
		//TravisFinder.run();
	}
}
