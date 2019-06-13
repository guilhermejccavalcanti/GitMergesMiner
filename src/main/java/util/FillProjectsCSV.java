package util;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FilenameFilter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FillProjectsCSV {
	
	final static String PATH = "/usr/miner/projects/";

	public static void main(String[] args) {
		try{
			File folder = new File(PATH);
			String[] directories = folder.list(new FilenameFilter() {
				@Override
				public boolean accept(File current, String name) {
					return new File(current, name).isDirectory();
				}
			});

			StringBuilder builder = new StringBuilder();
			builder.append("name,url");
			builder.append("\n");
			for(int i = 0; i<directories.length; i++){
				builder.append(directories[i]);
				builder.append(",");
				builder.append(PATH);
				builder.append(directories[i]);
				builder.append("\n");
			}

			Path path = Paths.get("projects.csv");
			try (BufferedWriter writer = Files.newBufferedWriter(path)){
				writer.write(builder.toString());
			}
		}catch(Exception e){
			e.printStackTrace();
		}
	}
}
