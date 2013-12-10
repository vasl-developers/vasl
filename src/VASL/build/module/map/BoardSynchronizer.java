/*
 * $Id: BoardSynchronizer 12/2/13 davidsullivan1 $
 *
 * Copyright (c) 2013 by David Sullivan
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Library General Public
 * License (LGPL) as published by the Free Software Foundation.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Library General Public License for more details.
 *
 * You should have received a copy of the GNU Library General Public
 * License along with this library; if not, copies are available
 * at http://www.opensource.org.
 */
package VASL.build.module.map;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import java.io.File;
import java.io.IOException;

public class BoardSynchronizer  {

    private static final String REMOTE_URL = "https://github.com/davidsullivan317/testRepro.git";
    private static final String LOCAL_REPRO_FOLDER = "C:\\Users\\usulld2\\AppData\\Local\\Temp\\myRepro";

    public static void main(String[] args) throws IOException, GitAPIException {

        // see if the local repository exists in the boards directory, if not clone it
        File localRepro = null;
        try {
            localRepro = new File(LOCAL_REPRO_FOLDER + File.separator + ".git");
            if (!localRepro.exists() || !localRepro.isDirectory()) {

                // then clone
                System.out.println("Cloning from " + REMOTE_URL + " to " + LOCAL_REPRO_FOLDER);
                Git.cloneRepository()
                        .setURI(REMOTE_URL)
                        .setDirectory(new File(LOCAL_REPRO_FOLDER))
                        .call();
            }
        } catch (GitAPIException e) {

            //TODO: log the error
            System.err.println("Unable to clone the boards repository: " + REMOTE_URL);
            e.printStackTrace();
        }

        // Open the local repository and pull any new/updated boards
        Repository repository = null;
        try {
            FileRepositoryBuilder builder = new FileRepositoryBuilder();
            repository = builder.setGitDir(localRepro)
                    .readEnvironment() // scan environment GIT_* variables
                    .findGitDir() // scan up the file system tree
                    .build();
            Git git = new Git(repository);
            git.pull().call();
        } catch (Exception e) {

            //TODO: log the error
            System.err.println("Unable to update the boards");
            e.printStackTrace();
        }
        finally {
            repository.close();
        }
    }
}
