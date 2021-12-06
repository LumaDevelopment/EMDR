package net.lumadevelopment.emdr.views.emdr;

import java.util.Timer;
import java.util.TimerTask;

import org.libsdl.SDL;
import org.libsdl.SDL_Error;
import org.vaadin.addon.sliders.PaperSlider;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;

import net.lumadevelopment.emdr.views.MainLayout;
import uk.co.electronstudio.sdl2gdx.RumbleController;
import uk.co.electronstudio.sdl2gdx.SDL2ControllerManager;

/**
 * 
 * This is the main and only view for the webpage.
 * This uses TimerTasks and other asynchronous
 * methods like listeners to not stall the page
 * forever with loops. Controls all tasks and listeners.
 * 
 * @author Luma Development
 *
 */

@PageTitle("EMDR")
@Route(value = "frontend/index.html", layout = MainLayout.class)
@RouteAlias(value = "", layout = MainLayout.class)
public class EMDRView extends VerticalLayout {

	/*
	 * While unpleasing to look at, this type of
	 * variable initialization is Vaadin convention
	 * and also helpful for the multiple
	 * TimerTasks used thorughout the page. 
	 */
	
	//Controller Manager
	public SDL2ControllerManager manager;
	
	//Timer Variables
	public Timer emdrTimer;
	public Timer uiTimer;
	
	//Thread-Modifiable Web Components
	public Span timeRunning;
	public Span ctrlCount;
	public PaperSlider slider;
	public Span status;
	
	//Default EMDR Variables
	private int duration = 500;
	private int gap = 150;
	private float intensity = 0.5f;
	private long seconds = 0;
	
	/*
	 * These web components are only established
	 * and modified within the scope of EMDRView()
	 */
	private Span sliderHeader;
	private Button toggle;
	private Span durationLabel;
	private Span gapLabel;
	private IntegerField durationField;
	private IntegerField gapField;
	private Button updateTiming;

    public EMDRView() {
    	
    	//Initialize time running counter
    	timeRunning = new Span("Time Active: ");
    	uiTimer = new Timer();
    	
    	//Add the component to the page
    	add(timeRunning);
    	
    	//Put time running update on schedule
    	uiTimer.scheduleAtFixedRate(new TimeRunningCounter(UI.getCurrent()), 0, 1000);
    	
    	/*
    	 * Set the timer early even before the page loads,
    	 * just in case a function that calls for it gets
    	 * called early. Don't want any NullPointers.
    	 */
    	emdrTimer = new Timer();
    	
    	//Initialize controllers
    	SDL.SDL_SetHint("SDL_XINPUT_ENABLED", "0");
    	manager = new SDL2ControllerManager();
    	
        setMargin(true);
        
        //Initialize controller count
        ctrlCount = new Span("Controller Count: " + controllerCount());
        add(ctrlCount);
        
        //Keeps controller count accurate
        uiTimer.scheduleAtFixedRate(new ControllerCount(UI.getCurrent()), 0, 50);
        
        //Status and Toggle
        status = new Span("EMDR: Off");
        toggle = new Button("Toggle EMDR");
        
        add(status, toggle);
        
        //When the "Toggle EMDR" button is pressed
        toggle.addClickListener(e -> {
        	
        	if(status.getText().contains("On")) {
        	
        		/*
        		 * If EMDR is already on, cancel the active tasks
        		 * and set the text to indicate it being off.
        		 */
        		
        		emdrTimer.cancel();
        		emdrTimer = new Timer();
        		status.setText("EMDR: Off");
        		
        	} else {
        		
        		/*
        		 * If there aren't two controllers connected,
        		 * don't allow EMDR to be turned on. If so,
        		 * all is well.
        		 */
        		
        		if(controllerCount() != 2) {
        			
        			Notification.show("Must have exactly 2 controllers to use EMDR");
        			
        		} else {
        			
        			//commonly used code delegated to function
        			runEMDR();
            		status.setText("EMDR: On");
            		
        		}
        		
        	}
        	
        });
        
        //Duration and Gap
        
        durationLabel = new Span("Duration: " + duration + "ms");
        gapLabel = new Span("Gap: " + gap + "ms");
        
        /*
         * These fields are rather small and work well
         * together so they are put together horizontally
         * instead of vertically above the update button.
         */
        
        HorizontalLayout timing = new HorizontalLayout();
        
        durationField = new IntegerField("Duration (ms)");
        gapField = new IntegerField("Gap (ms)");
        
        updateTiming = new Button("Update Duration and Gap");
        
        // Add all of this to page
        timing.add(durationField, gapField);
        add(durationLabel, gapLabel, timing, updateTiming);
        
        //Update button listener
        updateTiming.addClickListener(e -> {
        	
        	//Reset the EMDR timer
        	if(status.getText().contains("On")) {
        		emdrTimer.cancel();
        		emdrTimer = new Timer();
        	}
        	
        	/*
        	 * If the values in the fields are actually
        	 * there, set the duration/gap variables.
        	 */
        	
        	if(durationField.getValue() != null) {
        		
        		duration = durationField.getValue();
        		durationLabel.setText("Duration: " + duration + "ms");
        		
        	}
        	
        	if(gapField.getValue() != null) {
        		
        		gap = gapField.getValue();
        		gapLabel.setText("Gap: " + gap + "ms");
        		
        	}
        	
        	//Run the EMDR tasks with new timings
        	if(status.getText().contains("On")) { runEMDR(); }
        	
        });
        
        //Intensity Slider
        sliderHeader = new Span("Intensity (Value: " + (intensity * .01f) + "%)");
        
        /*
         * 1->99 because setting rumble intensity
         * to 0.0 or 1.0 throws errors
         */
        
        HorizontalLayout sliderLine = new HorizontalLayout();
        slider = new PaperSlider(1, 99, 50);
        
        //.01*% for use with controller.rumble()
        slider.addValueChangeListener(e -> {
        	sliderHeader.setText("Intensity (Value: " + slider.getValue() + "%)");
        	intensity = e.getValue() * .01f;
        });
        
        sliderLine.add(slider);
        
        add(sliderHeader, sliderLine);
        
        /*
         * At this point the page is completely rendered!
         * Due to the async nature of page rendering everything
         * else is delegated to TimerTasks or listeners.
         */
        
    }
    
    public void runEMDR() {
    	
    	/*
    	 * Just a double check of the controller count.
    	 * If there isn't 2, disable EMDR and show
    	 * an alert on the page.
    	 */
    	
    	if(controllerCount() != 2) {
    		emdrTimer.cancel();
    		emdrTimer = new Timer();
    		status.setText("EMDR: Off");
    		
    		Notification.show("Must have exactly 2 controllers to use EMDR");
    	}
    	
    	/*
    	 * These timing values allow the gap and duration
    	 * to be whatever is desired and still scale/
    	 * run in sequence properly.
    	 */
    	
    	//Controller 1 Rumble
    	emdrTimer.scheduleAtFixedRate(new TimerTask() {
    		
    		@Override
    		public void run() {
    			
    			RumbleController controller = (RumbleController) manager.getControllers().get(0);
    			controller.rumble(intensity, intensity, duration);
    			
    		}
    		
    	}, 0, (duration+gap)*2);
    	
    	//Controller 2 Rumble
    	emdrTimer.scheduleAtFixedRate(new TimerTask() {
    		
    		@Override
    		public void run() {
    			
    			RumbleController controller = (RumbleController) manager.getControllers().get(1);
    			controller.rumble(intensity, intensity, duration);
    			
    		}
    		
    	}, duration+gap, (duration+gap)*2);
    	
    }
    
    //Get Controller Count
    public int controllerCount() {
    	
    	/*
    	 * You constantly need to pollState() the
    	 * controller manager to keep an accurate
    	 * controller count
    	 */
    	
    	try {
			
			manager.pollState();
			
		} catch (SDL_Error e) {
			
			e.printStackTrace();
			
		}
    	
    	return manager.getControllers().size;
    	
    }
    
    /*
     * This keeps the Time Running field updated
     * every second. It's nice that this task
     * doesn't take so long to execute that
     * the field becomes inaccurate. This was
     * a little bit of a worry because of the
     * while loop and data processing but
     * it all worked out.
     */
    
    class TimeRunningCounter extends TimerTask {
    	
    	private final UI ui;
    	
    	public TimeRunningCounter(UI ui) {
    		this.ui = ui;
    	}
    	
    	@Override
    	public void run() {
			seconds++;
			
			String info = "";
			long secondsActive = seconds;
			
			/*
			 * This logic prevents things like 0h120m0s and
			 * other visually displeasing/nonsensical displays
			 * from occuring.
			 */
			
			int hrs = 0, mins = 0, sec = 0;
			
			//Count up hrs and mins before settling on seconds
			while(secondsActive > 59) {
				if(secondsActive > 3599) {
					secondsActive -= 3600;
					hrs++;
				} else {
					secondsActive -= 60;
					mins++;
				}
			}
			
			sec = (int) secondsActive;
			
			//If hrs == 0, don't show.
			if(hrs > 0) {
				info += "" + hrs + "h";
			}
			
			info += "" + mins + "m" + sec + "s";
			
			String setTo = "Time Active: " + info;
			
			ui.access(() -> timeRunning.setText(setTo));
    	}
    	
    }
    
    //Keep the controller count constantly updated
    class ControllerCount extends TimerTask {
    	
    	private final UI ui;
    	
    	public ControllerCount(UI ui) {
    		this.ui = ui;
    	}
    	
    	@Override
    	public void run() {
    		
    		ui.access(() -> ctrlCount.setText("Controller Count: " + controllerCount()));
    		
    	}
    	
    }

}
