package org.davinci.server.review.command;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.davinci.server.Command;
import org.davinci.server.ServerManager;
import org.davinci.server.user.User;

public class GetReviewUserInfo extends Command {

	public void handleCommand(HttpServletRequest req, HttpServletResponse resp,
			User user) throws IOException {
		this.responseString="{ userName: '"+user.getUserName()+
		"', isLocalInstall: "+String.valueOf(ServerManager.LOCAL_INSTALL)+
		" ,email: '"+user.getPerson().getEmail()+"' }";
	}

}
