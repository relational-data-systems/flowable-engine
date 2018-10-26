package org.flowable.app.security;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.flowable.app.model.common.RemoteUser;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class SkipAuthenticationFilter extends OncePerRequestFilter
{
  public static String USER_NAME = "user.name";

  public static String AUTHORITY_ACCESS_MODELER = "access-modeler";

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException
  {
    String userName = System.getProperty(USER_NAME);

    org.flowable.idm.api.User user = new RemoteUser();
    user.setId(userName);
    user.setFirstName(userName);
    List authorities = new ArrayList<>();
    authorities.add(new SimpleGrantedAuthority(AUTHORITY_ACCESS_MODELER));
    FlowableAppUser appUser = new FlowableAppUser(user, user.getId(), authorities);
    SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(appUser, "", authorities));

    filterChain.doFilter(request, response);

  }
}
