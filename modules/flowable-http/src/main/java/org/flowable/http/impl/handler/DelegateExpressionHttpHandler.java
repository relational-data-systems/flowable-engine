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
import org.flowable.engine.delegate.Expression;
import org.flowable.engine.impl.bpmn.helper.DelegateExpressionUtil;
import org.flowable.engine.impl.bpmn.parser.FieldDeclaration;
import org.flowable.engine.impl.context.Context;
import org.flowable.http.HttpRequest;
import org.flowable.http.HttpResponse;
import org.flowable.http.delegate.HttpRequestHandler;
import org.flowable.http.delegate.HttpResponseHandler;
import org.flowable.http.impl.delegate.HttpRequestHandlerInvocation;
import org.flowable.http.impl.delegate.HttpResponseHandlerInvocation;

/**
 * @author Tijs Rademakers
 */
public class DelegateExpressionHttpHandler implements HttpRequestHandler, HttpResponseHandler {
    
    private static final long serialVersionUID = 1L;
    
    protected Expression expression;
    protected final List<FieldDeclaration> fieldDeclarations;

    public DelegateExpressionHttpHandler(Expression expression, List<FieldDeclaration> fieldDeclarations) {
        this.expression = expression;
        this.fieldDeclarations = fieldDeclarations;
    }

    public void handleHttpRequest(DelegateExecution execution, HttpRequest httpRequest, CloseableHttpClient client) {
        Object delegate = DelegateExpressionUtil.resolveDelegateExpression(expression, execution, fieldDeclarations);
        if (delegate instanceof HttpRequestHandler) {
            Context.getProcessEngineConfiguration().getDelegateInterceptor().handleInvocation(
                            new HttpRequestHandlerInvocation((HttpRequestHandler) delegate, execution, httpRequest, client));
        } else {
            throw new FlowableIllegalArgumentException("Delegate expression " + expression + " did not resolve to an implementation of " + HttpRequestHandler.class);
        }
    }
    
    public void handleHttpResponse(DelegateExecution execution, HttpResponse httpResponse) {
        Object delegate = DelegateExpressionUtil.resolveDelegateExpression(expression, execution, fieldDeclarations);
        if (delegate instanceof HttpResponseHandler) {
            Context.getProcessEngineConfiguration().getDelegateInterceptor().handleInvocation(
                            new HttpResponseHandlerInvocation((HttpResponseHandler) delegate, execution, httpResponse));
        } else {
            throw new FlowableIllegalArgumentException("Delegate expression " + expression + " did not resolve to an implementation of " + HttpResponseHandler.class);
        }
    }

    /**
     * returns the expression text for this execution listener. Comes in handy if you want to check which listeners you already have.
     */
    public String getExpressionText() {
        return expression.getExpressionText();
    }
}
