/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.flowable.http.impl.handler;

import java.util.List;

import org.apache.http.impl.client.CloseableHttpClient;
import org.flowable.engine.common.api.FlowableIllegalArgumentException;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.impl.bpmn.helper.AbstractClassDelegate;
import org.flowable.engine.impl.bpmn.parser.FieldDeclaration;
import org.flowable.engine.impl.context.Context;
import org.flowable.http.HttpRequest;
import org.flowable.http.HttpResponse;
import org.flowable.http.delegate.HttpRequestHandler;
import org.flowable.http.delegate.HttpResponseHandler;
import org.flowable.http.impl.delegate.HttpRequestHandlerInvocation;
import org.flowable.http.impl.delegate.HttpResponseHandlerInvocation;

/**
 * Helper class for HTTP handlers to allow class delegation.
 * 
 * This class will lazily instantiate the referenced classes when needed at runtime.
 * 
 * @author Tijs Rademakers
 */
public class ClassDelegateHttpHandler extends AbstractClassDelegate implements HttpRequestHandler, HttpResponseHandler {

    private static final long serialVersionUID = 1L;

    public ClassDelegateHttpHandler(String className, List<FieldDeclaration> fieldDeclarations) {
        super(className, fieldDeclarations);
    }
    
    public ClassDelegateHttpHandler(Class<?> clazz, List<FieldDeclaration> fieldDeclarations) {
        super(clazz, fieldDeclarations);
    }
    
    @Override
    public void handleHttpRequest(DelegateExecution execution, HttpRequest httpRequest, CloseableHttpClient client) {
        HttpRequestHandler httpRequestHandler = getHttpRequestHandlerInstance();
        Context.getProcessEngineConfiguration().getDelegateInterceptor().handleInvocation(new HttpRequestHandlerInvocation(httpRequestHandler, execution, httpRequest, client));
    }

    @Override
    public void handleHttpResponse(DelegateExecution execution, HttpResponse httpResponse) {
        HttpResponseHandler httpResponseHandler = getHttpResponseHandlerInstance();
        Context.getProcessEngineConfiguration().getDelegateInterceptor().handleInvocation(new HttpResponseHandlerInvocation(httpResponseHandler, execution, httpResponse));
    }

    protected HttpRequestHandler getHttpRequestHandlerInstance() {
        Object delegateInstance = instantiateDelegate(className, fieldDeclarations);
        if (delegateInstance instanceof HttpRequestHandler) {
            return (HttpRequestHandler) delegateInstance;
        } else {
            throw new FlowableIllegalArgumentException(delegateInstance.getClass().getName() + " doesn't implement " + HttpRequestHandler.class);
        }
    }

    protected HttpResponseHandler getHttpResponseHandlerInstance() {
        Object delegateInstance = instantiateDelegate(className, fieldDeclarations);
        if (delegateInstance instanceof HttpResponseHandler) {
            return (HttpResponseHandler) delegateInstance;
        } else {
            throw new FlowableIllegalArgumentException(delegateInstance.getClass().getName() + " doesn't implement " + HttpResponseHandler.class);
        }
    }

}
