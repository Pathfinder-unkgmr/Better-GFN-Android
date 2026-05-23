(function() {
    'use strict';

    if (window.BetterGFNInitialized) return;
    window.BetterGFNInitialized = true;

    console.log("Better GFN Native Injector Loaded");

    const state = {
        enableGyro: false,
        enableRumble: false,
        enableStick: false,
        gyroX: 0,
        gyroY: 0,
        stickX: 0,
        stickY: 0,
        resolution: '1080p',
        fps: '60',
        brightness: 100,
        saturation: 100
    };

    window.BetterGFN = {
        updateState: function(key, value) {
            state[key] = value;
            console.log("BetterGFN State Updated:", key, value);
            if (key === 'brightness' || key === 'saturation') {
                applyFilters();
            } else if (key === 'enableStick') {
                if (value) createTouchStick();
                else removeTouchStick();
            }
        },
        updateAxes: function(gyroX, gyroY) {
            state.gyroX = gyroX;
            state.gyroY = gyroY;
        }
    };

    function applyFilters() {
        const video = document.querySelector('video');
        if (video) {
            video.style.filter = `brightness(${state.brightness}%) saturate(${state.saturation}%)`;
        }
    }
    const filterObserver = new MutationObserver(() => {
        const video = document.querySelector('video');
        if (video && !video.style.filter.includes(`brightness(${state.brightness}%)`)) {
            applyFilters();
        }
    });
    const startObserving = setInterval(() => {
        if (document.documentElement) {
            filterObserver.observe(document.documentElement, { childList: true, subtree: true });
            clearInterval(startObserving);
        }
    }, 100);

    const _origGetGamepads = navigator.getGamepads.bind(navigator);
    navigator.getGamepads = function() {
        const real = _origGetGamepads();
        if (!real) return real;

        const list = Array.from(real);
        let changed = false;

        for (let i = 0; i < list.length; i++) {
            const pad = list[i];
            if (!pad) continue;

            const clone = {
                id: pad.id,
                index: pad.index,
                connected: pad.connected,
                mapping: pad.mapping,
                timestamp: pad.timestamp,
                buttons: pad.buttons.map(b => ({ pressed: b.pressed, touched: b.touched, value: b.value })),
                axes: [...pad.axes],
                vibrationActuator: pad.vibrationActuator || null
            };

            if (state.enableGyro || state.enableStick) {
                clone.axes[2] = state.stickX + state.gyroX;
                clone.axes[3] = state.stickY + state.gyroY;
                clone.axes[2] = Math.max(-1, Math.min(1, clone.axes[2]));
                clone.axes[3] = Math.max(-1, Math.min(1, clone.axes[3]));
                clone.timestamp = performance.now();
                changed = true;
            }

            if (state.enableRumble) {
                clone.vibrationActuator = {
                    type: 'dual-rumble',
                    playEffect: (type, params) => {
                        if (window.AndroidNative && window.AndroidNative.playRumble) {
                            const intensity = Math.max(params.weakMagnitude, params.strongMagnitude);
                            window.AndroidNative.playRumble(intensity, params.duration);
                        }
                        return Promise.resolve('complete');
                    }
                };
                changed = true;
            }

            if (changed) list[i] = clone;
        }
        return changed ? list : real;
    };

    const STICK_RADIUS = 50; 
    let stickContainer = null;
    let stickKnob = null;
    let activeStickTouch = null;

    function createTouchStick() {
        if (stickContainer) return;

        stickContainer = document.createElement('div');
        stickContainer.id = 'bgfn-stick-container';
        Object.assign(stickContainer.style, {
            position: 'fixed',
            right: '5vw',
            bottom: '15vh',
            width: '20vw',
            height: '20vw',
            maxWidth: '120px',
            maxHeight: '120px',
            minWidth: '80px',
            minHeight: '80px',
            background: 'rgba(255,255,255,0.15)',
            borderRadius: '50%',
            border: '2px solid rgba(255,255,255,0.4)',
            zIndex: '9998',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            touchAction: 'none',
            userSelect: 'none'
        });

        stickKnob = document.createElement('div');
        Object.assign(stickKnob.style, {
            width: '45%',
            height: '45%',
            background: 'rgba(255,255,255,0.6)',
            borderRadius: '50%',
            position: 'absolute',
            left: '27.5%',
            top: '27.5%',
            transition: 'transform 0.05s',
            pointerEvents: 'none'
        });

        const label = document.createElement('div');
        Object.assign(label.style, {
            position: 'absolute',
            bottom: '-22px',
            width: '100%',
            textAlign: 'center',
            color: 'rgba(255,255,255,0.6)',
            fontSize: '10px',
            fontFamily: 'Arial, sans-serif',
            pointerEvents: 'none'
        });
        label.textContent = 'R-STICK';

        stickContainer.appendChild(stickKnob);
        stickContainer.appendChild(label);

        stickContainer.addEventListener('touchstart', (e) => {
            e.preventDefault();
            activeStickTouch = e.touches[0];
        }, { passive: false });

        stickContainer.addEventListener('touchmove', (e) => {
            e.preventDefault();
            if (!activeStickTouch) return;
            const touch = Array.from(e.touches).find(t => t.identifier === activeStickTouch.identifier);
            if (!touch) return;

            const rect = stickContainer.getBoundingClientRect();
            const centerX = rect.left + rect.width / 2;
            const centerY = rect.top + rect.height / 2;

            let dx = touch.clientX - centerX;
            let dy = touch.clientY - centerY;

            const magnitude = Math.hypot(dx, dy);
            if (magnitude > STICK_RADIUS) {
                dx = (dx / magnitude) * STICK_RADIUS;
                dy = (dy / magnitude) * STICK_RADIUS;
            }

            state.stickX = dx / STICK_RADIUS;
            state.stickY = dy / STICK_RADIUS;

            stickKnob.style.transform = `translate(${dx}px, ${dy}px)`;
            stickKnob.style.transition = 'none';
        }, { passive: false });

        const releaseStick = (e) => {
            e.preventDefault();
            activeStickTouch = null;
            state.stickX = 0;
            state.stickY = 0;
            stickKnob.style.transform = 'translate(0, 0)';
            stickKnob.style.transition = 'transform 0.15s';
        };

        stickContainer.addEventListener('touchend', releaseStick, { passive: false });
        stickContainer.addEventListener('touchcancel', releaseStick, { passive: false });

        const tryAttach = setInterval(() => {
            let targetContainer = document.fullscreenElement || 
                                  document.webkitFullscreenElement ||
                                  document.getElementById('StreamHud') || 
                                  document.getElementById('fullscreen-container') || 
                                  document.body;
            if (targetContainer) {
                targetContainer.appendChild(stickContainer);
                clearInterval(tryAttach);
            }
        }, 500);
        
        setInterval(() => {
            if (!stickContainer) return;
            let targetContainer = document.fullscreenElement || 
                                  document.webkitFullscreenElement ||
                                  document.getElementById('StreamHud') || 
                                  document.getElementById('fullscreen-container') || 
                                  document.body;
            if (targetContainer && !targetContainer.contains(stickContainer)) {
                targetContainer.appendChild(stickContainer);
            }
        }, 1000);
    }

    function removeTouchStick() {
        if (stickContainer) {
            stickContainer.remove();
            stickContainer = null;
            stickKnob = null;
            state.stickX = 0;
            state.stickY = 0;
        }
    }

})();
