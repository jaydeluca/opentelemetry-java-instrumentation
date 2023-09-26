package io.opentelemetry.javaagent.instrumentation.grails;

import grails.artefact.Controller;
import grails.core.GrailsApplication;
import grails.core.GrailsControllerClass;
import grails.databinding.CollectionDataBindingSource;
import grails.web.Action;
import grails.web.UrlConverter;
import grails.web.mapping.LinkGenerator;
import grails.web.mapping.mvc.RedirectEventListener;
import grails.web.mime.MimeUtility;
import grails.web.mvc.FlashScope;
import grails.web.servlet.mvc.GrailsParameterMap;
import groovy.lang.Closure;
import groovy.lang.Writable;
import org.grails.web.converters.Converter;
import org.grails.web.servlet.mvc.ActionResultTransformer;
import org.grails.web.servlet.mvc.GrailsWebRequest;
import org.grails.web.servlet.mvc.TokenResponseHandler;
import org.grails.web.sitemesh.GroovyPageLayoutFinder;
import org.grails.web.util.GrailsApplicationAttributes;
import org.springframework.context.ApplicationContext;
import org.springframework.validation.BindingResult;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.support.RequestDataValueProcessor;

import javax.servlet.ServletContext;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.ERROR;

class ErrorController implements Controller {

  @Action
  void index() {
    render(ERROR.getBody());
  }

  @Action
  void notFound() throws IOException {
    getResponse().sendError(404, "Not Found");
  }



}
