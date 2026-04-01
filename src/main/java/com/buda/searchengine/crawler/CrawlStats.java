package com.buda.searchengine.crawler;

public class CrawlStats {
    private int filesFound = 0;
    private int directoriesTraversed = 0;
    private int permissionDenied = 0;
    private int symlinksSkipped = 0;
    private int filteredOut = 0;

    public void incrementFilesFound() { filesFound++; }
    public void incrementDirectoriesTraversed() { directoriesTraversed++; }
    public void incrementPermissionDenied() { permissionDenied++; }
    public void incrementSymlinksSkipped() { symlinksSkipped++; }
    public void incrementFilteredOut() { filteredOut++; }

    public int getFilesFound() { return filesFound; }
    public int getDirectoriesTraversed() { return directoriesTraversed; }
    public int getPermissionDenied() { return permissionDenied; }
    public int getSymlinksSkipped() { return symlinksSkipped; }
    public int getFilteredOut() { return filteredOut; }
}