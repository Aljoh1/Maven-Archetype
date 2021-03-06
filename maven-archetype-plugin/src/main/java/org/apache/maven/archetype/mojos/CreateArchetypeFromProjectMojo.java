package org.apache.maven.archetype.mojos;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import org.apache.maven.archetype.ArchetypeCreationRequest;
import org.apache.maven.archetype.ArchetypeCreationResult;
import org.apache.maven.archetype.ArchetypeManager;
import org.apache.maven.archetype.common.ArchetypeRegistryManager;
import org.apache.maven.archetype.common.Constants;
import org.apache.maven.archetype.ui.ArchetypeCreationConfigurator;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.util.PropertyUtils;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/**
 * Creates an archetype from the current project.
 *
 * @author rafale
 * @requiresProject true
 * @goal create-from-project
 * @execute phase="generate-sources"
 * @aggregator
 */
public class CreateArchetypeFromProjectMojo
    extends AbstractMojo
{
    /** @component */
    private MavenProjectBuilder projectBuilder;

    /** @component */
    ArchetypeCreationConfigurator configurator;

    /**
     * Enable the interactive mode to define the archetype from the project.
     *
     * @parameter expression="${interactive}" default-value="false"
     */
    private boolean interactive;

    /** @component */
    ArchetypeRegistryManager archetypeRegistryManager;

    /** @component */
    ArchetypeManager archetype;

    /**
     * File extensions which are checked for project's text files (vs binary files).
     *
     * @parameter expression="${archetype.filteredExtentions}"
     */
    private String archetypeFilteredExtentions;

    /**
     * File extensions which are excluded from a project
     *
     * @parameter expression="${archetype.excludedExtentions}"
     */
    private String archetypeExcludedExtentions;

    /**
     * File extensions which are excluded from a project
     *
     * @parameter expression="${sourceDirectory}"
     */
    private String sourceDirectory;

    /**
     * Directory names which are checked for project's sources main package.
     *
     * @parameter expression="${archetype.languages}"
     */
    private String archetypeLanguages;

    /**
     * The location of the registry file.
     *
     * @parameter expression="${user.home}/.m2/archetype.xml"
     */
    private File archetypeRegistryFile;

    /**
     * Velocity templates encoding.
     *
     * @parameter default-value="UTF-8" expression="${archetype.encoding}"
     */
    private String defaultEncoding;

    /**
     * Create a partial archetype.
     *
     * @parameter expression="${archetype.partialArchetype}"
     */
    private boolean partialArchetype = false;

    /**
     * Create pom's velocity templates with CDATA preservation. This uses the <code>String.replaceAll()</code>
     * method and risks to have some overly replacement capabilities (beware of '1.0' value).
     *
     * @parameter expression="${archetype.preserveCData}"
     */
    private boolean preserveCData = false;

    /** @parameter expression="${localRepository}" */
    private ArtifactRepository localRepository;

    /**
     * POMs in archetype are created with their initial parent.
     * This property is ignored when preserveCData is true.
     *
     * @parameter expression="${archetype.keepParent}"
     */
    private boolean keepParent = true;

    /**
     * The Maven project to create an archetype from.
     *
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    private MavenProject project;

    /**
     * The property file that holds the plugin configuration.
     *
     * @parameter expression="${archetype.properties}"
     */
    private File propertyFile;

    /**
     * The property telling which phase to call on the generated archetype.
     * Interesting values are: <code>package</code>, <code>integration-test</code>, <code>install</code> and <code>deploy</code>.
     *
     * @parameter expression="${archetype.postPhase}" default-value="package"
     */
    private String archetypePostPhase;

    /**
     * The directory where the archetype should be created.
     * 
     * @parameter expression="${project.build.directory}/generated-sources/archetype"
     */
    private File outputDirectory;

    /** @parameter expression="${testMode}" */
    private boolean testMode;

    /** @parameter expression="${packageName}" */
    private String packageName; //Find a better way to resolve the package!!! enforce usage of the configurator

    /**
     *  @parameter expression="${session}"
     *  @readonly
     */
    private MavenSession session;

    public void execute()
        throws MojoExecutionException, MojoFailureException
    {
        Properties executionProperties = session.getExecutionProperties();
        try
        {
            if ( propertyFile != null )
            {
                propertyFile.getParentFile().mkdirs();
            }

            MavenProject        sourceProject;
            if ( sourceDirectory != null )
            {
                sourceProject = loadSourceDirectory();
            }
            else
            {
                sourceProject = project;
            }
            List<String> languages = getLanguages( archetypeLanguages, propertyFile );

            Properties properties =
                configurator.configureArchetypeCreation( sourceProject, Boolean.valueOf( interactive ), executionProperties,
                                                         propertyFile, languages );

            List<String> filtereds = getFilteredExtensions( archetypeFilteredExtentions, propertyFile );

            List<String> excludeds = getExcludedExtensions( archetypeExcludedExtentions, propertyFile );

            ArchetypeCreationRequest request = new ArchetypeCreationRequest()
                .setProject( sourceProject )
                /* Used when in interactive mode */
                .setProperties( properties )
                .setLanguages( languages )
                /* Should be refactored to use some ant patterns */
                .setFiltereds( filtereds )
                .setExcludeds( excludeds )
                /* This should be correctly handled */
                .setPreserveCData( preserveCData )
                .setKeepParent( keepParent )
                .setPartialArchetype( partialArchetype )
                /* This should be used before there and use only languages and filtereds */
                .setArchetypeRegistryFile( archetypeRegistryFile )
                .setLocalRepository( localRepository )
                /* this should be resolved and asked for user to verify */
                .setPackageName( packageName )
                .setPostPhase( archetypePostPhase )
                .setOutputDirectory( outputDirectory );

            ArchetypeCreationResult result = archetype.createArchetypeFromProject( request );

            if ( result.getCause() != null )
            {
                throw new MojoFailureException( result.getCause(), result.getCause().getMessage(),
                                                result.getCause().getMessage() );
            }

            getLog().info( "Archetype created in " + outputDirectory );

            if ( testMode )
            {
                // Now here a properties file would be useful to write so that we could automate
                // some functional tests where we string together an:
                //
                // archetype create from project -> deploy it into a test repo
                // project create from archetype -> use the repository we deployed to archetype to
                // generate
                // test the output
                //
                // This of course would be strung together from the outside.
            }

        }
        catch ( MojoFailureException ex )
        {
            throw ex;
        }
        catch ( Exception ex )
        {
            throw new MojoFailureException( ex, ex.getMessage(), ex.getMessage() );
        }
    }

    private MavenProject loadSourceDirectory()
            throws IOException, XmlPullParserException, ComponentLookupException, ProjectBuildingException
    {
        final File pomFile = new File(sourceDirectory, Constants.ARCHETYPE_POM);
        return projectBuilder.build(pomFile, localRepository, null);
    }

    private List<String> getExcludedExtensions( String archetypeExcludedExtentions, File propertyFile )
    {
        List<String> excludedExtensions = new ArrayList<String>();

        if ( StringUtils.isNotEmpty( archetypeExcludedExtentions ) )
        {
            excludedExtensions.addAll( Arrays.asList( StringUtils.split( archetypeExcludedExtentions, "," ) ) );

            getLog().debug( "Found in command line extensions = " + excludedExtensions );
        }

        if ( excludedExtensions.isEmpty() && propertyFile != null && propertyFile.exists() )
        {
            Properties properties = PropertyUtils.loadProperties( propertyFile );

            String extensions = properties.getProperty( Constants.ARCHETYPE_EXCLUDED_EXTENSIONS );
            if ( StringUtils.isNotEmpty( extensions ) )
            {
                excludedExtensions.addAll( Arrays.asList( StringUtils.split( extensions, "," ) ) );
            }

            getLog().debug( "Found in propertyFile " + propertyFile.getName() + " extensions = " + excludedExtensions );
        }

        return excludedExtensions;
    }

    private List<String> getFilteredExtensions( String archetypeFilteredExtentions, File propertyFile )
    {
        List<String> filteredExtensions = new ArrayList<String>();

        if ( StringUtils.isNotEmpty( archetypeFilteredExtentions ) )
        {
            filteredExtensions.addAll( Arrays.asList( StringUtils.split( archetypeFilteredExtentions, "," ) ) );

            getLog().debug( "Found in command line extensions = " + filteredExtensions );
        }

        if ( filteredExtensions.isEmpty() && propertyFile != null && propertyFile.exists() )
        {
            Properties properties = PropertyUtils.loadProperties( propertyFile );

            String extensions = properties.getProperty( Constants.ARCHETYPE_FILTERED_EXTENSIONS );
            if ( StringUtils.isNotEmpty( extensions ) )
            {
                filteredExtensions.addAll( Arrays.asList( StringUtils.split( extensions, "," ) ) );
            }

            getLog().debug( "Found in propertyFile " + propertyFile.getName() + " extensions = " + filteredExtensions );
        }

        if ( filteredExtensions.isEmpty() )
        {
            filteredExtensions.addAll( Constants.DEFAULT_FILTERED_EXTENSIONS );

            getLog().debug( "Using default extensions = " + filteredExtensions );
        }

        return filteredExtensions;
    }

    private List<String> getLanguages( String archetypeLanguages, File propertyFile )
    {
        List<String> resultingLanguages = new ArrayList<String>();

        if ( StringUtils.isNotEmpty( archetypeLanguages ) )
        {
            resultingLanguages.addAll( Arrays.asList( StringUtils.split( archetypeLanguages, "," ) ) );

            getLog().debug( "Found in command line languages = " + resultingLanguages );
        }

        if ( resultingLanguages.isEmpty() && propertyFile != null && propertyFile.exists() )
        {
            Properties properties = PropertyUtils.loadProperties( propertyFile );

            String languages = properties.getProperty( Constants.ARCHETYPE_LANGUAGES );
            if ( StringUtils.isNotEmpty( languages ) )
            {
                resultingLanguages.addAll( Arrays.asList( StringUtils.split( languages, "," ) ) );
            }

            getLog().debug( "Found in propertyFile " + propertyFile.getName() + " languages = " + resultingLanguages );
        }

        if ( resultingLanguages.isEmpty() )
        {
            resultingLanguages.addAll( Constants.DEFAULT_LANGUAGES );

            getLog().debug( "Using default languages = " + resultingLanguages );
        }

        return resultingLanguages;
    }
}
