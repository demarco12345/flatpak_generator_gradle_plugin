package com.demarco.flatpak;

import java.io.File;
import javax.inject.Inject;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.process.ExecOperations;

public class FlatpakGeneratorPlugin implements Plugin<Project> 
{
	private final ExecOperations execOperations;

	@Inject
	public FlatpakGeneratorPlugin(ExecOperations execOperations) 
	{
		this.execOperations = execOperations;
	}

	@Override
	public void apply(Project project) 
	{    	   	
		FlatpakExtension extension = project.getExtensions().create("flatpak", FlatpakExtension.class);

		// 1. Task: ./gradlew generateFlatpakManifest
		Task generateTask = project.getTasks().create("generateFlatpakManifest", GenerateFlatpakManifestTask.class, task -> {
			task.getAppId().set(extension.getAppId());
			task.getCommand().set(extension.getCommand());
			task.setGroup("publishing");
	
			Task bootJarTask = project.getTasks().findByName("bootJar");
			Task regularJarTask = project.getTasks().findByName("jar");
            
			if (bootJarTask != null) 
			{
				task.dependsOn(bootJarTask);
			} 
			else if (regularJarTask != null) 
			{
				task.dependsOn(regularJarTask);
			}

			task.doFirst(t -> {
				String jarName = bootJarTask != null ? "blueberry-irrigation.jar" : project.getName() + "-" + project.getVersion() + ".jar";
				File sourceJar = new File(project.getLayout().getBuildDirectory().get().getAsFile(), "libs/" + jarName);
				File targetDir = new File(project.getLayout().getBuildDirectory().get().getAsFile(), "flatpak");
	
				if (sourceJar.exists()) 
				{
					project.copy(copySpec -> {
						copySpec.from(sourceJar);
						copySpec.into(targetDir);
					});
				}
			});
		});

        	// 2. Task: ./gradlew buildFlatpakBundle
        	project.getTasks().register("buildFlatpakBundle", task -> {
            	task.setGroup("publishing");
            	task.setDescription("Compiles the flatpak sandbox environment and outputs an installable .flatpak file.");
            	task.dependsOn(generateTask);

            	task.doLast(t -> {
                	String appId = extension.getAppId().isPresent() ? extension.getAppId().get() : "org.example." + project.getName();
                	File workingDir = new File(project.getBuildDir(), "flatpak");
	
                	project.getLogger().lifecycle("Building flatpak bundle inside: " + workingDir.getAbsolutePath());
	
                	execOperations.exec(spec -> {
                    	spec.workingDir(workingDir);
                    	spec.commandLine("flatpak-builder", "--force-clean", "--repo=repo", "build-dir", appId + ".json");
                	});
	
                	execOperations.exec(spec -> {
                    	spec.workingDir(workingDir);
                    	spec.commandLine("flatpak", "build-bundle", "repo", "blueberry-irrigation.flatpak", appId);
                	});

                	project.getLogger().lifecycle("SUCCESS: Standalone package ready at: " + new File(workingDir, "blueberry-irrigation.flatpak").getAbsolutePath());
            	});
        	});

		// 3. Task: ./gradlew flatpakUninstall
		project.getTasks().register("flatpakUninstall", task -> {
			task.setGroup("publishing");
			task.setDescription("Uninstalls the app's local user-level flatpak footprint.");

            		task.doLast(t -> {
                		String appId = extension.getAppId().isPresent() ? extension.getAppId().get() : "org.example." + project.getName();
                		project.getLogger().lifecycle("Removing sandbox installation for: " + appId);

                		// Executes the native flatpak uninstall check sequence asynchronously
                		execOperations.exec(spec -> {
                    		spec.commandLine("flatpak", "uninstall", "--user", "--delete-data", "--assumeyes", appId);
                    		spec.setIgnoreExitValue(true); // Prevents task crashes if the app isn't currently installed
                		});
                
                		project.getLogger().lifecycle("Clean up operations complete.");
            		});
        	});
    }
}
