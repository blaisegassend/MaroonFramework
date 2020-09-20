package com.team766.framework;

import java.util.function.BooleanSupplier;
import com.team766.hal.RobotProvider;
import com.team766.logging.Category;
import com.team766.logging.Logger;
import com.team766.logging.Severity;

public final class Context implements Runnable {
	private static enum ControlOwner {
		MAIN_THREAD,
		SUBROUTINE,
    }
    private static enum State {
        RUNNING,
        CANCELED,
        DONE,
    }

    private RunnableWithContext m_func;
	private Thread m_thread;
	private Object m_threadSync;
	private State m_state;
	private BooleanSupplier m_blockingPredicate;
	private ControlOwner m_controlOwner;
    private String m_previousWaitPoint;
    
	public Context(RunnableWithContext func) {
        m_func = func;
		m_threadSync = new Object();
		m_previousWaitPoint = null;
		m_controlOwner = ControlOwner.MAIN_THREAD;
		m_state = State.RUNNING;
		m_thread = new Thread(this::threadFunction);
        m_thread.start();
        Scheduler.getInstance().add(this);
    }

    public Context(Runnable func) {
        this((context) -> func.run());
    }
    
    public String getContextName() {
        return "Context/" + Integer.toHexString(hashCode()) + "/" + m_func.toString();
    }

    @Override
    public String toString() {
        return getContextName();
    }

	private String getExecutionPoint() {
		StackTraceElement[] stack = Thread.currentThread().getStackTrace();
		for (int i = 0; i < stack.length; ++i) {
			if (stack[i].getClassName() == this.getClass().getName()) {
				return stack[i].toString();
			}
		}
		return null;
	}
	
	private void waitForControl(ControlOwner thisOwner) {
		if (thisOwner == ControlOwner.SUBROUTINE) {
			String waitPointTrace = getExecutionPoint();
			if (waitPointTrace != null && !waitPointTrace.equals(m_previousWaitPoint)) {
				Logger.get(Category.PROCEDURES).logRaw(Severity.INFO, getContextName() + " is waiting at " + waitPointTrace);
				m_previousWaitPoint = waitPointTrace;
			}
		}
		synchronized (m_threadSync) {
			while (m_controlOwner != thisOwner && m_state != State.DONE) {
				try {
					m_threadSync.wait();
				} catch (InterruptedException e) {
				}
			}
            m_controlOwner = thisOwner;
            if (m_state != State.RUNNING && m_controlOwner == ControlOwner.SUBROUTINE) {
                throw new ContextStoppedException();
            }
		}
	}
	
	private void transferControl(ControlOwner thisOwner, ControlOwner desiredOwner) {
		synchronized (m_threadSync) {
			if (m_controlOwner != thisOwner) {
				throw new IllegalStateException("Subroutine had control owner " + m_controlOwner + " but assumed control owner " + thisOwner);
			}
			m_controlOwner = desiredOwner;
			m_threadSync.notifyAll();
			waitForControl(thisOwner);
		}
	}
	
	private void threadFunction() {
		waitForControl(ControlOwner.SUBROUTINE);
		try {
			m_func.run(this);
		} finally {
			synchronized (m_threadSync) {
				m_state = State.DONE;
				m_threadSync.notifyAll();
			}
		}
	}
	
	public void waitFor(BooleanSupplier predicate) {
		while (!predicate.getAsBoolean()) {
            m_blockingPredicate = predicate;
			transferControl(ControlOwner.SUBROUTINE, ControlOwner.MAIN_THREAD);
		}
	}

	public void yield() {
		m_blockingPredicate = null;
		transferControl(ControlOwner.SUBROUTINE, ControlOwner.MAIN_THREAD);
	}

	public void waitForSeconds(double seconds) {
		double startTime = RobotProvider.instance.getClock().getTime();
		waitFor(() -> RobotProvider.instance.getClock().getTime() - startTime > seconds);
	}

	public void startAsync(RunnableWithContext func) {
		new Context(func);
    }

    public void startAsync(Runnable func) {
        new Context(func);
    }

    public void stop() {
        synchronized (m_threadSync) {
            if (m_state != State.DONE) {
                m_state = State.CANCELED;
            }
        }
    }
    
	public final void run() {
        if (m_state == State.DONE) {
            Scheduler.getInstance().cancel(this);
            return;
        }
        if (m_state == State.CANCELED || m_blockingPredicate == null || m_blockingPredicate.getAsBoolean()) {
            transferControl(ControlOwner.MAIN_THREAD, ControlOwner.SUBROUTINE);
        }
    }

	public boolean isDone() {
		return m_state == State.DONE;
    }
    
    public void takeControl(Mechanism mechanism) {
        mechanism.takeControl(this);
	}
}