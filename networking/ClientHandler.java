package networking;

import java.io.File;
import java.io.IOException;
import sql.sqlQueries;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;

import com.google.gson.Gson;

import serialized.Packet;
import serialized.User;
import serialized.FileBytes;
import fileIO.*;
import json.GsonFunctions;
import json.ProjectNotes;
import json.Projects;
public class ClientHandler extends Thread {
	
	private ObjectInputStream ObjInput;
	private ObjectOutputStream ObjOutput;
	final Socket socket;
	private Packet userPacket;
	final static String PACKET_TYPE_USER = "user";
	final static String PACKET_TYPE_FILE = "file";
	final static String PACKET_TYPE_ERROR = "error";
	final static String FILE_TYPE_EXPORT = "export";
	final static String FILE_TYPE_IMPORT = "import";
	final static String USER_TYPE_LOGIN = "login";
	final static String USER_TYPE_REGISTER = "register";
	final static String USER_TYPE_ADD_TO_PROJECT = "add_user";
	final static String USER_TYPE_REGISTER_PROJECT = "register_project";
	final static String USER_TYPE_GUEST = "guest";
	final static String USER_FILE_REQUEST = "file_request";
	
	
	public ClientHandler(Socket s)
	{
		this.socket = s;
		try {
			this.ObjInput = new ObjectInputStream(socket.getInputStream());
			this.ObjOutput = new ObjectOutputStream(socket.getOutputStream());
		} catch (IOException e) {
			e.printStackTrace();
			System.err.println("error connecting to the client");
		}
	}
	
	@Override
	public void run() {
		boolean connected = true;
		while(connected) {
			
			try {
				userPacket = (Packet) ObjInput.readObject();
				
				System.err.println("Currently servicing user: " + userPacket.getUsername());
				
				String packet_type = userPacket.getPacketType();
				
				System.err.println(packet_type);
				
				switch(packet_type) {
					case PACKET_TYPE_USER:
						//function for connecting to sql database and checking user credentials
						User user = userPacket.getUser();
						String userType = user.getUserType();
						switch(userType) {
						
						case USER_TYPE_LOGIN:
							//login sql code
							String username = user.getUsername();
							String password = user.getPassword();
							boolean successful = sqlQueries.loginUser(username, password);
							User authenticatedUser = new User(username, password, successful);
							NetworkFunctions.sendUserToClient(ObjOutput, authenticatedUser);
							break;
						case USER_TYPE_REGISTER:
							//register sql code
							String username_register = user.getUsername();
							String password_register = user.getPassword();
							boolean successful_register = sqlQueries.registerUser(username_register, password_register);
							User successfulRegistration = new User(username_register, password_register, successful_register);
							NetworkFunctions.sendUserToClient(ObjOutput, successfulRegistration);
							break;
						case USER_TYPE_ADD_TO_PROJECT:
							// sql code to add a user to a project
							String user_to_add = user.getUsername();
							String project = user.getProject();
							String project_owner = user.getProjectOwner();
							boolean userAdded = sqlQueries.addUserToProject(user_to_add, project, project_owner);
							
							User addedUser = new User(user_to_add, "", userAdded);
							NetworkFunctions.sendUserToClient(ObjOutput, addedUser);
							
							break;
						case USER_TYPE_REGISTER_PROJECT:
							// sql function to register a project
							String project_to_register = user.getProject();
							String owner = user.getUsername();
							boolean success = sqlQueries.createNewProject(owner, project_to_register);
							User registration = new User(owner, project_to_register, success);
							NetworkFunctions.sendUserToClient(ObjOutput, registration);
							break;
						case USER_TYPE_GUEST:
							String guest = user.getUsername();
							String [] projects = sqlQueries.userProjects(guest);
							String [] owners = sqlQueries.ownersOfProjectsForUser(guest);
							
	
							
							User projects_to_send = new User(guest, projects, owners);
							
							if (projects == null) {
								projects_to_send.setSuccess(false);
							}else {
								projects_to_send.setSuccess(true);
							}
							NetworkFunctions.sendUserToClient(ObjOutput, projects_to_send);
							break;
						case USER_FILE_REQUEST:
							//sending a project to user
							String who_requested = user.getUsername();
							String proj = user.getProject();
							String dir_path = sqlQueries.getProjectPath(proj, who_requested);
							String target_dir = dir_path + File.separator + proj + ".zip";
							//int version = sqlQueries.createNewVersion(who_requested, proj);
							//String version = "\\version" + Integer.toString();
							
							
							FileZip zip = new FileZip(dir_path, target_dir);
							String config = dir_path.substring(0, dir_path.indexOf(proj)) + "config.txt";
							
							String to_json = GsonFunctions.parseServerJson(config).toJson();
							Projects p = new Gson().fromJson(to_json, Projects.class);
							String project_creator = target_dir.substring(dir_path.indexOf("Prohub" + "\\") + 7, dir_path.indexOf("\\" + proj)); 
							ProjectNotes curr_project_json = p.projectExists(proj, project_creator);
							String j = curr_project_json.toJson();
							
							
							
		
							zip.zipDir();
							FileTransfer sendToClient = new FileTransfer(ObjOutput);
							sendToClient.sendFileToClient(target_dir, j);
							File delete = new File(target_dir);
							delete.delete();
							break;
						}
						break;	
						
					case PACKET_TYPE_FILE:
						
						FileBytes file = userPacket.getFile();
						
						String requestType = file.getRequestType();
						
						switch(requestType) {
							case FILE_TYPE_IMPORT: //case for getting a project from user
								byte[] file_data = file.getFile();
								String dir = sqlQueries.getProjectPath(file.getProjectName(), file.getOwnerName());
								String fname = file.getProjectName() + ".zip";
								
								String srcdir = LocalFileIO.receiveFile(file_data, dir, fname);
								System.out.println(srcdir + " write to " + dir);
								File toDelete = new File(srcdir);
								FileZip unzip = new FileZip(srcdir, dir);
								unzip.unzipDir();
								System.out.println(toDelete.getName() + " is being deleted");
								toDelete.delete();
								
								String json_string = file.getJsonString();
								String pathname = "C:\\Users\\juanl\\Desktop\\Prohub\\" + file.getOwnerName() + "\\" + "config.txt";
								File exists = new File(pathname);
								if(exists.exists()) {
									Projects p = GsonFunctions.parseServerJson(pathname);
									p.updateProjectWithJsonString(json_string);
									GsonFunctions.createServerConfig(p.getProjects(), pathname);
								}else{
									ArrayList<ProjectNotes> projects = new ArrayList<ProjectNotes>();
									projects.add(new ProjectNotes(json_string));
									GsonFunctions.createServerConfig(projects, pathname);
								}
								
								break;		
						}
						break;
					case PACKET_TYPE_ERROR:
						System.err.println("tbh idk what happened");
						break;
					
				}
				
			
				
				
				
			} catch (ClassNotFoundException | IOException e) {
				connected = false;
				System.err.println("user disconnected");
			}
			
			
			
		}
	
	}
	
}
