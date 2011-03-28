/*
 * Copyright 2011, The gwtquery team.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.google.gwt.query.client.plugins.events;

import com.google.gwt.core.client.Duration;
import com.google.gwt.dom.client.Element;
import com.google.gwt.query.client.Function;
import com.google.gwt.query.client.js.JsObjectArray;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.EventListener;

/**
 * This class implements an event queue instance for one Element. The queue
 * instance is configured as the default event listener in GWT.
 * 
 * The reference to this queue is stored as a unique variable in the element's
 * DOM
 * 
 * The class takes care of calling the appropriate functions for each browser
 * event and it also calls sinkEvents method.
 * 
 */
public class EventsListener implements EventListener {

  private static class BindFunction {

    Object data;
    Function function;
    String nameSpace = "";
    int times = -1;
    int type;

    BindFunction(int t, String n, Function f, Object d) {
      type = t;
      function = f;
      data = d;
      if (n!=null) {
        nameSpace = n;
      }
    }

    BindFunction(int t, String n, Function f, Object d, int times) {
      this(t, n, f, d);
      this.times = times;
    }

    public boolean fire(Event event) {
      if (times != 0) {
        times--;
        return function.f(event, data);
      }
      return true;
    }

    public boolean hasEventType(int etype) {
      return (type & etype) == type;
    }
  }
  // Gwt Events class has not this event defined
  public static int ONSUBMIT = 0x08000;

  public static void clean(Element e) {
    EventsListener ret = getGQueryEventListener(e);
    if (ret != null){
      ret.clean();
    } 
  }

  public static EventsListener getInstance(Element e) {
    EventsListener ret = getGQueryEventListener(e);
    return ret != null ? ret : new EventsListener(e);
  }

  private static native void cleanGQListeners(Element elem) /*-{
		if (elem.__gwtlistener) {
			elem.__listener = elem.__gwtlistener;
		}
		elem.__gquerysubmit = null;
		elem.__gqueryevent = null

  }-*/;
  
  private static native EventsListener getGQueryEventListener(Element elem) /*-{
    return elem.__gqueryevent;
  }-*/;
  
  private static native EventListener getGwtEventListener(Element elem) /*-{
    return elem.__gwtlistener;
  }-*/;

  private static native void setGQueryEventListener(Element elem,
      EventsListener gqevent) /*-{
    if (elem.__gqueryevent) {
      elem.__listener = elem.__gqueryevent;
    } else {
      elem.__gwtlistener = elem.__listener;
      elem.__gqueryevent = gqevent;
    }  
  }-*/;

  // Gwt does't handle submit events in DOM.sinkEvents
  private static native void sinkSubmitEvent(Element elem) /*-{
    if (elem.__gquerysubmit) return;
    elem.__gquerysubmit = true;
    
    var handle = function(event) {
      elem.__gqueryevent.@com.google.gwt.query.client.plugins.events.EventsListener::dispatchEvent(Lcom/google/gwt/user/client/Event;)(event);
    };
    
    if (elem.addEventListener)
      elem.addEventListener("submit", handle, true);
    else
      elem.attachEvent("onsubmit", handle);
  }-*/;
  int eventBits = 0;
  double lastEvnt = 0;

  int lastType = 0;

  private Element element;

  private JsObjectArray<BindFunction> elementEvents = JsObjectArray
      .createArray().cast();
  
  private EventsListener(Element element) {
    this.element = element;
  }
  
  public void bind(int eventbits, final Object data, Function...funcs) {
    bind(eventbits, null, data, funcs);
  }

  public void bind(int eventbits, final Object data, final Function function,
      int times) {
    bind(eventbits, null, data, function, times);
  }
  
  public void bind(int eventbits, String name, final Object data, Function...funcs) {
    for (Function function: funcs) {
      bind(eventbits, name, data, function, -1);
    }
  }
  
  public void bind(int eventbits, String namespace, final Object data, final Function function,
      int times) {
    if (function == null) {
      unbind(eventbits, namespace);
      return;
    }
    eventBits |= eventbits;
    sink();
    elementEvents.add(new BindFunction(eventbits, namespace, function, data, times));
  }
  
  public void bind(String event, final Object data, Function...funcs) {
    String nameSpace = event.replaceFirst("^[^\\.]+\\.*(.*)$", "$1");
    String eventName = event.replaceFirst("^([^\\.]+).*$", "$1");
    int b = 0;
    if ("submit".equals(eventName)) {
      b = ONSUBMIT;
    } else {
      b = Event.getTypeInt(eventName);
    }
    for (Function function: funcs) {
      bind(b, nameSpace, data, function, -1);
    }
  }
  
  public void dispatchEvent(Event event) {
    int etype = "submit".equalsIgnoreCase(event.getType()) ? ONSUBMIT
        : DOM.eventGetType(event);
    for (int i = 0; i < elementEvents.length(); i++) {
      BindFunction listener = elementEvents.get(i);
      if (listener.hasEventType(etype)) {
        if (!listener.fire(event)) {
          event.stopPropagation();
          event.preventDefault();
        }
      }
    }
  }
  
  /**
   * Return the original gwt EventListener associated with
   * this element, before gquery replaced it to introduce its
   * own event handler.
   */
  public EventListener getOriginalEventListener() {
    return getGwtEventListener(element);
  }
  
  public void onBrowserEvent(Event event) {
    // Workaround for Issue_20
    if (lastType == event.getTypeInt()
        && lastEvnt - Duration.currentTimeMillis() < 10
        && "body".equalsIgnoreCase(element.getTagName())) {
      return;
    }
    lastEvnt = Duration.currentTimeMillis();
    lastType = event.getTypeInt();

    // Execute the original Gwt listener
    if (getOriginalEventListener() != null) {
      getOriginalEventListener().onBrowserEvent(event);
    }
    
    dispatchEvent(event);
  }
  
  public void rebind() {
    sink();
  }
  
  public void unbind(int eventbits) {
    unbind(eventbits, null);
  }
  
  public void unbind(int eventbits, String namespace) {
    JsObjectArray<BindFunction> newList = JsObjectArray
        .createArray().cast();
    for (int i = 0; i < elementEvents.length(); i++) {
      BindFunction listener = elementEvents.get(i);
      boolean matchNS = namespace == null || namespace.isEmpty() || listener.nameSpace.equals(namespace);
      boolean matchEV = eventbits <= 0 || listener.hasEventType(eventbits);
      if (matchNS && matchEV) {
        continue;
      }
      newList.add(listener);
    }
    elementEvents = newList;
  }
  
  public void unbind(String event) {
    String nameSpace = event.replaceFirst("^[^\\.]+\\.*(.*)$", "$1");
    String eventName = event.replaceFirst("^([^\\.]+).*$", "$1");
    int b = 0;
    if ("submit".equals(eventName)) {
      b = ONSUBMIT;
    } else {
      b = Event.getTypeInt(eventName);
    }
    unbind(b, nameSpace);
  }
  
  private void clean(){
    cleanGQListeners(element);
    elementEvents =  JsObjectArray.createArray().cast();
  }
  
  private void sink() {
    setGQueryEventListener(element, this);
    DOM.setEventListener((com.google.gwt.user.client.Element)element, this);
    if (eventBits == ONSUBMIT) {
      sinkSubmitEvent(element);
    } else {
      if ((eventBits | Event.FOCUSEVENTS) == Event.FOCUSEVENTS && element.getAttribute("tabIndex").length() == 0) {
        element.setAttribute("tabIndex", "0");
      }
      DOM.sinkEvents((com.google.gwt.user.client.Element) element, eventBits
          | DOM.getEventsSunk((com.google.gwt.user.client.Element) element));
      
    }
  }
}
