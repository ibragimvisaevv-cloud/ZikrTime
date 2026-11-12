(() => {
    "use strict";
    const el = id => document.getElementById(id);
    const tau = 2 * Math.PI;
    const designHeight = 854;
    const sky = el("sky").getContext("2d");
    const worlds = el("planets").getContext("2d");
    const strand = el("strand").getContext("2d");
    const backdrop = new Image();
    let width, height, unit, skyScale, ratio, viewPixels = null;
    let settings = null, latestTotal = 0, latestGoal = 0;
    let travel = null, pulses = [], queue = [], animation = 0, turns = 0;
    let strandDirty = true, lastWorlds = -Infinity, worldTime = 0, worldClock = 0;
    const strikes = new Set();
    let backdropSettled = false, fontSettled = !document.fonts, entryTimeout = 0;
    let skyPainted = false, backgroundReported = false;

    // Rasterize the polished stone in local sphere coordinates once. The broad,
    // rose-grey reflection underneath is separate from the small white window highlight.
    function makeSphere(size, shader) {
        const sprite = document.createElement("canvas");
        sprite.width = sprite.height = size;
        const ctx = sprite.getContext("2d"), pixels = ctx.createImageData(size, size);
        const radius = size / 2 - 1;
        for (let row = 0; row < size; row++) {
            for (let col = 0; col < size; col++) {
                const x = (col + .5 - size / 2) / radius;
                const y = (row + .5 - size / 2) / radius;
                const r = Math.hypot(x,y);
                if (r > 1 + .5 / radius) continue;
                const z = Math.sqrt(Math.max(0, 1 - r * r));
                const rgba = shader(x,y,z);
                const p = (row * size + col) * 4;
                pixels.data[p] = rgba[0];
                pixels.data[p+1] = rgba[1];
                pixels.data[p+2] = rgba[2];
                pixels.data[p+3] = 255 * rgba[3] * Math.min(1, Math.max(0,(1-r)*radius + .5));
            }
        }
        ctx.putImageData(pixels,0,0);
        return sprite;
    }

    function stone(seed) {
        return makeSphere(96,(x,y,z) => {
            const rim = (1-z) ** 3;
            const cloud = .76 * Math.sin(12*x + 4*Math.sin(7*y+seed) + seed)
                        + .6 * Math.cos(18*y - 8*x + seed);
            const diffuse = -13*x - 21*y + 8*z + 10*rim + cloud;
            const lower = Math.exp(-((x-.07)**2/.52 + (y-.62)**2/.10));
            const caustic = Math.exp(-((x-.09)**2/.076 + (y-.60)**2/.023));
            const dx = x+.31, dy = y+.43;
            const gleam = Math.exp(-((dx+.32*dy)**2/.014 + dy*dy/.010));
            const haze = Math.exp(-(dx*dx + dy*dy)/.13);
            const foot = Math.max(0,y);
            const reflection = Math.exp(-((x-.03)**2/.67+(y-.52)**2/.18));
            const shade = Math.max(0,x)*9+foot*foot*15;
            return [84+diffuse+46*lower+38*caustic+86*gleam+16*haze+17*foot+12*reflection,
                    139+diffuse+10*lower+26*caustic+67*gleam+13*haze-shade+5*reflection,
                    156+diffuse+3*lower+28*caustic+59*gleam+11*haze-1.36*shade-6*foot,
                    .89+.06*z];
        });
    }
    const glass = [stone(1),stone(3),stone(5),stone(9)];

    // Rest positions and apparent diameters follow the source frame. Orbits are
    // constructed through those positions in one inclined plane, around the fixed sun.
    const orbits = [
        {x:46, y:725, r:5.8, rate:.062, color:[40,43,68]},
        {x:214,y:726, r:10.0,rate:.090, color:[111,67,45], bands:true},
        {x:73, y:783, r:4.5, rate:.075, color:[95,47,37]},
        {x:294,y:772, r:3.0, rate:.16, color:[124,80,51]},
        {x:327,y:796, r:6.4, rate:.145, color:[185,148,102], bands:true},
        {x:166,y:828, r:7.4, rate:.119, color:[36,85,117], earth:true},
        {x:333,y:852, r:11.0,rate:.068, color:[196,161,100], rings:true, bands:true}
    ];
    const planeAngle = -.10, flatten = .265;
    const ca = Math.cos(planeAngle), sa = Math.sin(planeAngle);
    orbits.forEach((p,i) => {
        const dx = p.x-240, dy=p.y-788;
        const horizontal=dx*ca+dy*sa, depth=(-dx*sa+dy*ca)/flatten;
        p.distance=Math.hypot(horizontal,depth);
        p.phase=Math.atan2(depth,horizontal);
        p.sprite=makeSphere(64,(x,y,z) => {
            const light=Math.max(0,-.79*x-.34*y+.40*z);
            const bands = p.bands ? 8*Math.sin(16*y+2*x*z) + 5*Math.sin(36*y-.7*x) : 0;
            const terrain = Math.sin(8*x+2*Math.cos(6*y))+Math.cos(8*y+3*Math.sin(x*5));
            let color=p.color;
            if (p.earth && terrain>.10) color=[57,111,91];
            const d=.36+.66*light;
            return color.map(c => c*d+bands+3*terrain+3*Math.sin(19*x+17*y+i)).concat(1);
        });
    });

    function drawBackdrop() {
        if (!width || !backdrop.naturalWidth) return;
        // Preserve the sun's circular core. Fill ultrawide/landscape edges with
        // the outer cloud texture rather than stretching the solar disk.
        const sw=backdrop.naturalWidth, sh=backdrop.naturalHeight;
        const wholeW=sw*skyScale, left=(width-wholeW)/2;
        sky.drawImage(backdrop, left, 0, wholeW, height);
        if (left>0) {
            const slice=sw*.32;
            for (let distance=0, column=0; distance<left; distance+=slice*skyScale, column++) {
                for (const side of [-1,1]) {
                    const edge = side<0 ? left-distance : width-left+distance;
                    sky.save();
                    sky.translate(edge, 0);
                    sky.scale(side*(column%2===0?1:-1), 1);
                    const source = side<0 ? 0 : sw-slice;
                    const spriteW = slice*skyScale;
                    const destination = column%2===0 ? 0 : -spriteW;
                    if (side>0) {
                        sky.translate(2*destination+spriteW,0);
                        sky.scale(-1,1);
                    }
                    sky.drawImage(backdrop, source,0,slice,sh, destination-.6,0,spriteW+1.2,height);
                    sky.restore();
                }
            }
        }
        skyPainted=true;
        reportBackground();
    }

    function reportBackground() {
        if (!skyPainted || backgroundReported || document.body.dataset.entry!=="ready" ||
            !window.AndroidTasbih || typeof window.AndroidTasbih.backgroundReady!=="function") return;
        backgroundReported=true;
        // The host waits for this fully opaque canvas frame to reach its compositor.
        requestAnimationFrame(() => {
            if (window.AndroidTasbih) window.AndroidTasbih.backgroundReady();
        });
    }

    function drawWorlds(elapsed) {
        worlds.clearRect(0,0,width,height);
        worlds.save();
        worlds.translate(width*.5,height*788/designHeight);
        worlds.scale(skyScale,skyScale);
        for (const p of orbits) {
            const angle=p.phase + elapsed*p.rate;
            const u=p.distance*Math.cos(angle), v=p.distance*flatten*Math.sin(angle);
            const x=u*ca-v*sa, y=u*sa+v*ca;
            worlds.save();
            worlds.translate(x,y);
            if (p.rings) drawRings(p.r,false);
            worlds.save();
            // Keep the illuminated hemisphere towards the source at the center.
            worlds.rotate(Math.atan2(-y,-x) - Math.atan2(-.34,-.79));
            worlds.drawImage(p.sprite,-p.r,-p.r,p.r*2,p.r*2);
            worlds.restore();
            if (p.rings) drawRings(p.r,true);
            worlds.restore();
        }
        worlds.restore();
    }

    function drawRings(r,front) {
        worlds.save();
        worlds.rotate(.12);
        for (let i=0;i<4;i++) {
            worlds.beginPath();
            worlds.strokeStyle=["#89745a","#c1a079","#8e775d","#d0ad83"][i];
            worlds.globalAlpha=.84;
            worlds.lineWidth=i===2 ? .7 : 1;
            worlds.ellipse(0,0,r*(1.27+i*.22),r*(.25+i*.055),0,
                front?0:Math.PI,front?Math.PI:tau);
            worlds.stroke();
        }
        worlds.restore();
    }

    function positionArabic() {
        const text=el("arabic");
        if (!settings || !text.textContent) return;
        text.removeAttribute("transform");
        text.setAttribute("x",width/2);
        text.setAttribute("y",0);
        let font=66*unit;
        text.style.fontSize=font+"px";
        let bounds=text.getBBox();
        if (bounds.width>width*.86) {
            font*=width*.86/bounds.width;
            text.style.fontSize=font+"px";
            bounds=text.getBBox();
        }
        // BBox includes line metrics rather than the ink on several WebViews.
        // Canvas actual glyph bounds keep the vowel marks centered with the word.
        const ctx=strand;
        ctx.font=font+"px SceneAmiri, serif";
        ctx.direction="rtl";
        const measure=ctx.measureText(text.textContent);
        const ascent=measure.actualBoundingBoxAscent, descent=measure.actualBoundingBoxDescent;
        const baseline=Number.isFinite(ascent) ? (ascent-descent)*.5 : -bounds.y-bounds.height/2;
        text.setAttribute("y",baseline);
        text.setAttribute("transform","translate(0 "+height*.231+") scale(1 .83)");
        const phrase=el("phrase-control").style, caption=el("caption");
        const inkHeight=Number.isFinite(ascent+descent) ? ascent+descent : bounds.height;
        phrase.setProperty("--arabic-width",Math.max(48,bounds.width+12)+"px");
        phrase.setProperty("--arabic-height",Math.max(48,inkHeight*.83+8)+"px");
        phrase.setProperty("--caption-offset",(caption.offsetTop-height*.231-4)+"px");
        phrase.setProperty("--caption-width",Math.max(48,caption.offsetWidth+12)+"px");
        phrase.setProperty("--caption-height",Math.max(48,caption.offsetHeight+8)+"px");
    }

    function setViewport(physicalWidth,physicalHeight) {
        if (!(physicalWidth>0 && physicalHeight>0)) return;
        if (viewPixels && viewPixels.width===physicalWidth && viewPixels.height===physicalHeight) return;
        viewPixels={width:physicalWidth,height:physicalHeight};
        resize();
    }

    function resize() {
        const dpr=window.devicePixelRatio || 1;
        if (viewPixels) {
            // The AndroidView is measured by Compose. Pin an all-absolute
            // document to that measured size even when WebView initially
            // reports a zero-height layout viewport while it is attaching.
            width=viewPixels.width/dpr;
            height=viewPixels.height/dpr;
            for (const node of [document.documentElement,document.body]) {
                node.style.width=width+"px";
                node.style.height=height+"px";
            }
        } else {
            width=window.innerWidth || document.documentElement.clientWidth;
            height=window.innerHeight || document.documentElement.clientHeight;
        }
        if (!width || !height) return;
        unit=Math.min(width/480,height/designHeight);
        ratio=Math.min(3,dpr);
        skyScale=height/designHeight;
        document.body.style.setProperty("--u",unit+"px");
        // Only the two moving strips need high-DPI buffers. Coordinates stay in
        // the full scene's coordinate system so rotation and resizing agree.
        for (const [ctx, top, bottom, density] of [
            [sky, 0, 1, Math.min(1.5,ratio)],
            [worlds, .73, 1, ratio],
            [strand, .38, .66, ratio]
        ]) {
            const surface=ctx.canvas, stripHeight=height*(bottom-top);
            surface.style.top=100*top+"%";
            surface.style.height=100*(bottom-top)+"%";
            surface.width=Math.round(width*density);
            surface.height=Math.max(1,Math.round(stripHeight*density));
            const sy=surface.height/stripHeight;
            ctx.setTransform(surface.width/width,0,0,sy,0,-top*height*sy);
        }
        drawBackdrop();
        positionArabic();
        strandDirty=true;
        lastWorlds=-Infinity;
        requestFrame();
    }
    function showEntrance() {
        if (!settings) return;
        const body=document.body;
        const reduced=settings.reducedMotion ||
            (window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches);
        function finish() {
            clearTimeout(entryTimeout);
            body.dataset.entry="ready";
            reportBackground();
        }
        if (reduced) {
            finish();
            return;
        }
        if (body.dataset.entry!=="waiting") return;
        function reveal() {
            clearTimeout(entryTimeout);
            body.dataset.entry="entering";
            // A fixed endpoint also covers a paused or unsupported CSS animation.
            entryTimeout=setTimeout(finish,650);
        }
        if (backdropSettled && fontSettled) reveal();
        else if (!entryTimeout) entryTimeout=setTimeout(reveal,700);
    }

    backdrop.addEventListener("load",() => {
        drawBackdrop();
        backdropSettled=true;
        showEntrance();
    });
    backdrop.addEventListener("error",() => { backdropSettled=true; showEntrance(); });
    backdrop.src="nebula.webp";
    window.addEventListener("resize",resize);
    if (document.fonts) {
        const fontDone=() => {
            fontSettled=true;
            positionArabic();
            showEntrance();
        };
        document.fonts.load("44px SceneAmiri").then(fontDone,fontDone);
    }

    // Invert arc length along the parabola, so adjacent beads keep a constant
    // distance even as they climb the sides. Slopes are shallow and monotonic.
    function xAtDistance(distance,k) {
        let x=distance;
        for (let j=0;j<3;j++) {
            const slope=k*x, tangent=Math.hypot(1,slope);
            const length=(x*tangent+Math.asinh(slope)/k)/2;
            x-=(length-distance)/tangent;
        }
        return x;
    }

    function drawStrand(phase,now) {
        strand.clearRect(0,0,width,height);
        const mid=width/2, center=height*.55, sag=height*.09;
        const k=2*sag/(mid*mid), spacing=31.4*unit;
        const limit=Math.ceil((mid+sag)/spacing)+2;
        strand.beginPath();
        strand.moveTo(0,center-sag);
        strand.quadraticCurveTo(mid,center+sag,width,center-sag);
        strand.strokeStyle="rgba(196,201,189,.63)";
        strand.lineWidth=1.55*unit;
        strand.stroke();

        let kick=0;
        for (const start of pulses) {
            const age=now-start;
            if (age<240) kick=Math.max(kick,Math.sin(Math.PI*age/240)*.10);
        }
        for (let i=-limit; i<=limit; i++) {
            const index=i-phase;
            const x=xAtDistance(index*spacing+5.5*unit*Math.tanh(1.35*index),k);
            const y=center-k*x*x/2;
            const lift=Math.exp(-3.3*index*index);
            const radius=unit*(14.0-1.35*Math.min(1,Math.abs(x)/mid) + 4.25*lift)*(1+kick*lift);
            if (x+mid < -radius || x+mid > width+radius) continue;
            const variant=((i+turns)%glass.length+glass.length)%glass.length;
            strand.drawImage(glass[variant],mid+x-radius,y-radius,2*radius,2*radius);
        }
        for (const start of pulses) {
            const age=now-start;
            for (let i=0;i<2;i++) {
                const t=(age-i*85)/530;
                if (t<0 || t>=1) continue;
                strand.beginPath();
                strand.arc(mid,center,(19+28*t)*unit,0,tau);
                strand.strokeStyle="rgba(198,222,222,"+(.43*(1-t))+")";
                strand.lineWidth=1.15*unit;
                strand.stroke();
            }
        }
        if (pulses.length) {
            const t=(now-pulses[pulses.length-1])/490;
            if (t>=0 && t<1) {
                strand.font=(15.5*unit)+"px Roboto, sans-serif";
                strand.direction="ltr";
                strand.textAlign="left";
                strand.fillStyle="rgba(255,157,105,"+(.88*(1-t))+")";
                strand.fillText("+1",mid+26*unit,center-(26+16*t)*unit);
            }
        }
    }

    function requestFrame() {
        if (!animation && !document.hidden) animation=requestAnimationFrame(frame);
    }
    function frame(now) {
        animation=0;
        if (!width || document.hidden) return;
        const reduced=settings && settings.reducedMotion === true;
        if (lastWorlds===-Infinity || (!reduced && now-lastWorlds>=32)) {
            if (worldClock && !reduced) worldTime+=(now-worldClock)/1000;
            drawWorlds(worldTime);
            lastWorlds=worldClock=now;
        }
        let phase=0;
        if (travel) {
            const t=Math.min(1,Math.max(0,(now-travel.start)/travel.duration));
            phase=t*t*(3-2*t);
            if (t>=1) {
                turns=(turns+1)%glass.length;
                commitStep(travel,now,!reduced);
                travel=null;
                phase=0;
                startNext(now);
            }
        }
        const hadPulse=pulses.length>0;
        pulses=pulses.filter(start => now-start<615);
        if (travel || hadPulse || strandDirty) {
            drawStrand(phase,now);
            strandDirty=false;
        }
        if (!reduced || travel || pulses.length) requestFrame();
    }

    function renderNumber(total,goal) {
        const cycle=goal>0 && total>0 ? (total-1)%goal+1 : total;
        const fraction=goal>0 ? cycle/goal : 0;
        const angle=tau*fraction-Math.PI/2, length=tau*47;
        el("count").textContent=String(cycle);
        el("count").style.fontSize="calc("+Math.min(37.2,105/String(cycle).length)+" * var(--u))";
        el("goal").textContent=goal>0 ? "/ "+goal : "∞";
        el("arc").style.display=el("tip").style.display=fraction>0 ? "" : "none";
        el("arc").setAttribute("stroke-dasharray",(fraction*length)+" "+length);
        el("tip").setAttribute("cx",50+47*Math.cos(angle));
        el("tip").setAttribute("cy",50+47*Math.sin(angle));
        const countGoal=goal>0 ? cycle+" / "+goal : total+", "+((settings && settings.unlimitedLabel) || "∞");
        const complete=goal>0 && cycle===goal ? settings && settings.completedLabel : "";
        el("beads").setAttribute("aria-label",[settings && settings.tapLabel,countGoal,complete].filter(Boolean).join(", "));
        el("dial-control").setAttribute("aria-label",[settings && settings.settingsLabel,countGoal].filter(Boolean).join(", "));
    }
    function commitStep(event,now,effects) {
        renderNumber(event.total,event.goal);
        if (effects) pulses.push(now);
        const timer=setTimeout(() => {
            strikes.delete(timer);
            if (!document.hidden && window.AndroidTasbih) window.AndroidTasbih.strike();
        },160);
        strikes.add(timer);
        strandDirty=true;
    }
    function startNext(now) {
        if (travel || !queue.length) return;
        const event=queue.shift();
        travel={...event,start:now,duration:400/Math.min(5,1+queue.length*.65)};
        requestFrame();
    }
    function settle() {
        travel=null;
        queue=[];
        pulses=[];
        for (const timer of strikes) clearTimeout(timer);
        strikes.clear();
        renderNumber(latestTotal,latestGoal);
        strandDirty=true;
        requestFrame();
    }
    function updateCount(total,goal) {
        if (total===latestTotal && goal===latestGoal) return;
        latestTotal=total;
        latestGoal=goal;
        settle();
    }
    function configure(data) {
        const refresh=!settings || settings.arabic!==data.arabic ||
            settings.goal!==data.goal || settings.reducedMotion!==data.reducedMotion;
        settings=data;
        document.documentElement.lang=data.lang || "ru";
        el("arabic").textContent=data.arabic;
        el("transcription").textContent=data.transcription;
        el("translation").textContent=data.translation;
        for (const id of ["beads","phrase-control","dial-control"]) el(id).disabled=false;
        el("phrase-control").setAttribute("aria-label", [data.settingsLabel,data.transcription].filter(Boolean).join(", "));
        latestGoal=data.goal;
        latestTotal=data.count;
        if (refresh) settle();
        else if (!travel && !queue.length) renderNumber(data.count,data.goal);
        positionArabic();
        if (document.fonts) document.fonts.ready.then(positionArabic);
        showEntrance();
        worldClock=0;
        requestFrame();
    }
    function step(total,goal) {
        if (!settings || (total===latestTotal && goal===latestGoal)) return;
        latestTotal=total;
        latestGoal=goal;
        if (document.hidden) { settle(); return; }
        if (settings.reducedMotion) { commitStep({total,goal},performance.now(),false); requestFrame(); return; }
        queue.push({total,goal});
        startNext(performance.now());
    }
    window.tasbih={configure,step,updateCount,settle,setViewport};

    // HTML buttons synthesize the same click for touch, Enter, Space and TalkBack.
    // These targets are siblings of the bead button, so opening the controls cannot count.
    for (const id of ["phrase-control","dial-control"]) {
        el(id).addEventListener("click",() => {
            if (window.AndroidTasbih) window.AndroidTasbih.openSettings();
        });
    }

    const button=el("beads");
    button.dataset.testid="tasbih_counter";
    el("count").dataset.testid="tasbih_count_value";
    let gesture=null;
    const tap=() => { if (window.AndroidTasbih) window.AndroidTasbih.tap(); };
    button.addEventListener("pointerdown",e => {
        if (gesture || !e.isPrimary || e.button!==0) return;
        gesture={id:e.pointerId,x:e.clientX,y:e.clientY,moved:false,swipedDown:false};
        button.setPointerCapture(e.pointerId);
    });
    function trackGesture(e) {
        if (!gesture || gesture.id!==e.pointerId) return;
        const dy=e.clientY-gesture.y;
        if (Math.hypot(e.clientX-gesture.x,dy)>8) gesture.moved=true;
        if (dy>=46) gesture.swipedDown=true;
    }
    button.addEventListener("pointermove",trackGesture);
    button.addEventListener("pointerup",e => {
        if (!gesture || gesture.id!==e.pointerId) return;
        trackGesture(e); // A fast swipe can reach its endpoint without an intermediate move.
        const count=!gesture.moved || gesture.swipedDown;
        gesture=null;
        if (count) tap();
    });
    function cancelGesture(e) {
        if (gesture && gesture.id===e.pointerId) gesture=null;
    }
    button.addEventListener("pointercancel",cancelGesture);
    button.addEventListener("lostpointercapture",cancelGesture);
    button.addEventListener("click",e => {
        // Physical pointers commit on release. Enter, Space and TalkBack can
        // activate the same button directly without sending pointer events.
        if (e.detail===0 && !e.pointerType) tap();
    });
    document.addEventListener("visibilitychange",() => {
        if (document.hidden) {
            gesture=null;
            if (animation) cancelAnimationFrame(animation);
            animation=0;
            worldClock=0;
            settle();
        } else {
            lastWorlds=-Infinity;
            requestFrame();
        }
    });
    resize();
})();
