package org.davinci.server.user;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOCase;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.io.filefilter.NameFileFilter;
import org.apache.commons.io.filefilter.SuffixFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.davinci.ajaxLibrary.LibInfo;
import org.davinci.ajaxLibrary.Library;
import org.davinci.server.IDavinciServerConstants;
import org.davinci.server.IVResource;
import org.davinci.server.ServerManager;
import org.davinci.server.VDirectory;
import org.davinci.server.VFile;
import org.davinci.server.VLibraryResource;
import org.davinci.server.VResourceUtils;
import org.davinci.server.VProject;
import org.davinci.server.VWorkspace;
import org.davinci.server.internal.Activator;
import org.davinci.server.internal.Links;
import org.davinci.server.internal.Links.Link;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.osgi.framework.Bundle;

public class User {

	private File userDirectory;
	private File settingsDirectory;
	private Links links;
	private Person person;
	private VWorkspace workspace;
	private Hashtable libSettings;

	public User(Person person, IVResource userDirectory) throws URISyntaxException {
		this(person, new File(userDirectory.getURI()));
	}

	public User(Person person, File userDirectory) {
		this.person = person;
		this.userDirectory = userDirectory;
		this.userDirectory.mkdirs();
		
		this.workspace = new VWorkspace();
		/* assume all directories in the users space are projects */
		File[] projects = this.userDirectory.listFiles();
		for(int i=0;i<projects.length;i++){
			if(projects[i].isDirectory()){
				IVResource project = this.rebuildProject(projects[i].getName());
				this.workspace.add(project);
			}
		}
		
	}

	/* rebuilds the virtual part of the workspace.
	 * 
	 * any real files are NOT included in this data structure for 
	 * performance reasons. 
	 */
	
	public VProject rebuildProject(String projectName) {
		VProject project = new VProject(this.workspace,projectName);
		LibInfo libs[] = getLibs(projectName);
		for (int i = 0; i < libs.length; i++) {
			String defaultRoot = libs[i].getVirtualRoot();
			IPath path = new Path(defaultRoot);
			IVResource root = project;
			Library b = this.getLibrary(libs[i]);
			/* library not found on server so avoid adding it to the workspace */
			if (b == null) {
				continue;
			}

			for (int k = 0; k < path.segmentCount() - 1; k++) {
				String segment = path.segment(k);
				IVResource v = root.get(segment);
				if (v == null) {
					v = new VDirectory(root, segment);
					root.add(v);
				}
				root = v;
			}

			URL file = b.getURL("");
			// TODO temp fix to avoid adding virtual library entries that don't
			// exist to the workspace.
			if (file == null) {
				continue;
			}
			IVResource libResource = new VLibraryResource(b, file, root,path.lastSegment(), "");
			root.add(libResource);
		}
		return project;
	}

    public void initNewProject(File userDir) {
        List extensions = ServerManager.getServerManger().getExtensions(IDavinciServerConstants.EXTENSION_POINT_INITIAL_USER_FILES,
                IDavinciServerConstants.EP_TAG_INITIAL_USER_FILE);
        for (Iterator iterator = extensions.iterator(); iterator.hasNext();) {
            IConfigurationElement libraryElement = (IConfigurationElement) iterator.next();
            String path = libraryElement.getAttribute(IDavinciServerConstants.EP_ATTR_INITIAL_USER_FILE_PATH);
            String name = libraryElement.getDeclaringExtension().getContributor().getName();
            Bundle bundle = Activator.getActivator().getOtherBundle(name);

            VResourceUtils.copyDirectory(userDir, path, bundle);
        }

    }
	
	public void createProject(String projectName){
		
        File settingsDir = getSettingsDirectory(projectName);
        settingsDir.mkdir();
        initNewProject(getProjectFile(projectName));
        VProject project = this.rebuildProject(projectName);
        this.workspace.add(project);
        
	}
	
	private LibrarySettings getLibInfo(String project) {
		if (this.libSettings == null) {
			this.libSettings = new Hashtable();
		}
		if(!this.libSettings.contains(project))
			this.libSettings.put(project, new LibrarySettings(this.getSettingsDirectory(project)));
		return (LibrarySettings)this.libSettings.get(project);
	}

	public File getUserDirectory() {
		return this.userDirectory;
	}

	public void modifyLibrary(String id, String version, String project, boolean installed) {
		LibrarySettings libs = this.getLibInfo(project);

		if (!installed) {
			libs.removeLibrary(id, version);

		} else {
			String defaultRoot = ServerManager.getServerManger().getLibraryManager().getDefaultRoot(id, version);
			libs.addLibrary(id, version, id, defaultRoot);
		}
		rebuildProject(project);
	}

	public void modifyLibrary(String id, String version, String project, String virtualRoot) {
		LibrarySettings libs = this.getLibInfo(project);

		libs.modifyLibrary(id, version, virtualRoot);
		rebuildProject(project);
	}

	
	public IVResource[] listFiles(String path, String project) {
	    IVResource[] found = new IVResource[0];
		
	    
	   
	    if (path == null || path.equals(".") ) {
			path = "";
		}
        
	    
    
		/* list all files given a path, dont recurse. */
		/* add users actual workspace files */
		IVResource r1 = getUserFile(path, project);
		if (r1 != null) {
			if (r1.isDirectory()) {
				found = r1.listFiles();
			}
		}
		/* add links */
		r1 = getLinkedResource(path,project);
		if (r1 != null) {
            if (r1.isDirectory()) {
                IVResource[] list = r1.listFiles();
                found = VResourceUtils.merge(found, list);
            }
        }
		
		
		r1 = getLibFile(path,project);
        if (r1 != null) {
            if (r1.isDirectory()) {
                IVResource[] list = r1.listFiles();
                found = VResourceUtils.merge(found, list);
            }
        }
 		return found;
 		

	}

	private void findLibFiles(IPath path, String project, ArrayList results) {
	
		IVResource[] result = this.workspace.find(project, path.toString());

		for (int i = 0; i < result.length; i++) {
			results.add(result[i]);
		}
	}

	public IVResource getResource(String path, String project) {

	    IVResource r1 = getUserFile(path, project);
        if (r1 != null) {
            return r1;
        }
        /* add links */
        r1 = getLinkedResource(path, project);
        if (r1 != null) {
           return r1;
        }
        
        return getLibFile(path, project);
    }

	protected IVResource[] getRootLibraryEntries() {
		return this.workspace.listFiles();

	}


	private Library getLibrary(LibInfo li) {
		String id = li.getId();
		String version = li.getVersion();
		return ServerManager.getServerManger().getLibraryManager()
				.getLibrary(id, version);

	}

	private IVResource getLibFile(String p1, String project) {
		IPath path = new Path(p1);
		IVResource root = this.getProject(project);
		for (int i = 0; i < path.segmentCount() && root != null; i++) {
			root = root.get(path.segment(i));

		}

		return root;
	}

	private IVResource getLinkedResource(String path, String project){

	    String path1 = path;
        if (path1.startsWith("./")) {
            path1 = path.substring(2);
        } else if (path.length() > 0 && path.charAt(0) == '.') {
            path1 = path.substring(1);
        }
	    Link link = this.getLinks(path1).hasLink(path1);
        if (link != null) {
            path = link.location + "/" + path1.substring(link.path.length());
            path = path.replace('/', File.separatorChar);
            VFile linkFile = new VFile(new File(path));
            return linkFile;
        }
        return null;
        
	}
	
	
	 private IVResource getUserFile(String p1, String project) {
	       
	        String path = p1;
	        while(path.length()> 0 && (path.charAt(0)=='.' || path.charAt(0)=='/' || path.charAt(0)=='\\'))
            	path=path.substring(1);


	       File userProject = this.getProjectFile(project);

	        IPath a = new Path(userProject.getAbsolutePath()).append(path);
	        /*
	         * security check, dont want to return a resource BELOW the workspace
	         * root
	         */
	        IPath workspaceRoot = new Path(userProject.getAbsolutePath());
	        if (a.matchingFirstSegments(workspaceRoot) != workspaceRoot.segmentCount()) {
	            return null;
	        }

	        File f1 = new File(a.toOSString());

	        if (!f1.exists()) {

	            IPath a2 = new Path(userProject.getAbsolutePath()).append(path + IDavinciServerConstants.WORKING_COPY_EXTENSION);
	            File workingCopy = new File(a2.toOSString());
	            if (!workingCopy.exists()) {
	                return null;
	            }
	        }
	        String[] segments = a.segments();
	        IPath me = new Path(userProject.getAbsolutePath());
	        IVResource parent = this.getProject(project);
	        for (int i = me.matchingFirstSegments(a); i < segments.length; i++) {
	            int segsToEnd = segments.length - i - 1;
	            String s = a.removeLastSegments(segsToEnd).toOSString();
	            File f = new File(s);
	            parent = new VFile(f, parent, segments[i]);
	        }
	        
	        if(parent==this.getProject(project))
	            parent = new VFile(userProject, getProject(project));
	        
	        return parent;

	}

	public IVResource createResource(String path, String project) {
		/* serve working copy files if they exist */

		String path1 = path;
		if (path1.startsWith("./")) {
			path1 = path.substring(2);
		} else if (path.length() > 0 && path.charAt(0) == '.') {
			path1 = path.substring(1);
		}

		Link link = this.getLinks(project).hasLink(path1);
		if (link != null) {
			path = link.location + "/" + path1.substring(link.path.length());
			path = path.replace('/', File.separatorChar);
			VFile linkFile = new VFile(new File(path));
			return linkFile;
		}

		IVResource directory = new VFile(this.userDirectory, this.workspace);

		IVResource userFile = directory.create(path);

		return userFile;
	}

	private File getProjectFile(String project){
		return new File(this.userDirectory,project);
	}
	
	private VProject getProject(String project){
		return (VProject)this.workspace.get(project);
	}
	
	public File getSettingsDirectory(String project) {
		File settings = null;
		if(project!=null){
			// get per project settings
			File projectDirectory = getProjectFile(project);
			settings = new File(projectDirectory,IDavinciServerConstants.SETTINGS_DIRECTORY_NAME );
		}else{
			// workbench global settings 
			settings = new File(this.userDirectory,IDavinciServerConstants.SETTINGS_DIRECTORY_NAME );
		}
		return settings;
	}

	synchronized public Links getLinks(String project) {
		if (this.links == null) {
			this.links = new Links(this.getSettingsDirectory(project));
		}
		return this.links;
	}

	public IVResource[] findFiles(String pathStr, boolean ignoreCase,boolean workspaceOnly, String project) {

		return this.findFiles(pathStr, ".", ignoreCase, workspaceOnly, project);
	}

	public IVResource[] findFiles(String pathStr, String startFolder,	boolean ignoreCase, boolean workspaceOnly, String project) {
		boolean isWildcard = pathStr.indexOf('*') >= 0;
		IPath path = new Path(pathStr);
		ArrayList results = new ArrayList();

		// Links links = this.getLinks();
		if (isWildcard) {
			IOFileFilter filter;
			if (path.segment(0).equals("*")) {
				IOCase ioCase = ignoreCase ? IOCase.INSENSITIVE		: IOCase.SENSITIVE;
				filter = new NameFileFilter(path.lastSegment(), ioCase);
			} else {
				String lastSegment = path.lastSegment();
				if (lastSegment.startsWith("*")) {
					filter = new SuffixFileFilter(lastSegment.substring(1));
				} else {
					filter = null;
				}
			}
			// big todo here, have to remove the file filter
			
			File f1 = null; 
		    if (startFolder == null || startFolder.equals(".")) {
		          f1 = getProjectFile(project);
		     } else {
		         IVResource start = this.getUserFile(startFolder, project);
		         if(start!=null)
    		         try {
    		             
    		                 f1 = new File(start.getURI());
                    } catch (URISyntaxException e) {
                        e.printStackTrace();
                    }
		     }
		    if(f1!=null){
    			Collection c = FileUtils.listFiles(f1, filter, TrueFileFilter.INSTANCE);
    			File[] found = (File[]) c.toArray(new File[c.size()]);
    			for (int i = 0; i < found.length; i++) {
    					File workspaceFile = this.getProjectFile(project);
    
    					IPath workspacePath = new Path(workspaceFile.getPath());
    					IPath foundPath = new Path(found[i].getPath());
    					IPath elementPath = foundPath.makeRelativeTo(workspacePath);
    
    					IVResource[] wsFound = this.findFiles(elementPath.toString(), ignoreCase, true,project);
    					results.addAll(Arrays.asList(wsFound));
    
    			}
		    }
			
			Link[] allLinks = links.allLinks();
			for (int i = 0; i < allLinks.length; i++) {
				File file = new File(allLinks[i].location);
				Collection c = FileUtils.listFiles(file, filter, TrueFileFilter.INSTANCE);
				File[] found = (File[]) c.toArray(new File[c.size()]);

				for (int p = 0; p < found.length; p++) {
					IPath workspacePath = new Path(this.getUserDirectory()
							.getPath());
					IPath foundPath = new Path(found[p].getPath());
					IPath elementPath = foundPath.makeRelativeTo(workspacePath);

					IVResource[] wsFound = this.findFiles(elementPath.toString(), ignoreCase, true,project);
					results.addAll(Arrays.asList(wsFound));

				}

			}

			if (!workspaceOnly) {
				this.findLibFiles(path, project, results);

			}
		} else {
			IVResource file = this.getResource(pathStr,project);
			if (file != null && file.exists()) {
				results.add(file);
			}

		}
		return (IVResource[]) results.toArray(new IVResource[results.size()]);

	}

	public LibInfo[] getLibs(String project) {
		return this.getLibInfo(project).allLibs();
	}

	public String getLibPath(String id, String version, String project) {
		/*
		 * returns the virtual path of library in the users workspace given ID
		 * and version for now its going to be the default, but this will allow
		 * to remap/move etc..
		 */
		LibInfo[] mappedLibs = this.getLibs(project);
		for (int i = 0; i < mappedLibs.length; i++) {
			LibInfo library = mappedLibs[i];
			if (library.getId().equals(id)
					&& library.getVersion().equals(version)) {
				return library.getVirtualRoot();
			}
		}

		return null;
	}

	public File getGlobalSettings(){
		return   new File(this.getUserDirectory(), IDavinciServerConstants.SETTINGS_DIRECTORY_NAME);
	}
	
	public String getUserName() {
		return this.person.getUserName();
	}

	public Person getPerson() {
		return this.person;
	}

	public IVResource[] listProjects() {
		
		return this.workspace.listFiles();
	}

}
