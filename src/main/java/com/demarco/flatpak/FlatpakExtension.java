package com.demarco.flatpak;

import org.gradle.api.provider.Property;

public interface FlatpakExtension 
{
    // The reverse-DNS ID for Flathub (e.g., "org.example.MyApp")
    Property<String> getAppId();
    
    // The command used to run the application (e.g., "run.sh")
    Property<String> getCommand();
}
