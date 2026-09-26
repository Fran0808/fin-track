import { useEffect, useRef } from 'react';
import { Noise } from './waveNoise';


export function LoginWaves() {
  const canvasRef = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    const context = canvas?.getContext('2d');
    if (!canvas || !context) return;

    const motionPreference = window.matchMedia('(prefers-reduced-motion: reduce)');
    const noise = new Noise(0.42);
    const duration = 4800;
    let width = 0;
    let height = 0;
    let elapsed = 0;
    let previousTime = 0;
    let lastDraw = 0;
    let frame = 0;
    let visible = true;
    let color = '';

    function draw() {
      if (!context) return;
      context.clearRect(0, 0, width, height);
      context.beginPath();
      context.strokeStyle = color;
      context.lineWidth = 1;

      // Ease the ambient movement to a static composition in less than five seconds.
      const progress = 1 - Math.pow(1 - Math.min(elapsed / duration, 1), 2);
      const time = progress * duration;
      for (let x = -40; x <= width + 40; x += 26) {
        for (let y = -30; y <= height + 30; y += 14) {
          const move = noise.perlin2((x + time * 0.012) * 0.002, (y + time * 0.006) * 0.0015) * 12;
          const waveX = x + Math.cos(move) * 24;
          const waveY = y + Math.sin(move) * 10;
          if (y === -30) context.moveTo(waveX, waveY);
          else context.lineTo(waveX, waveY);
        }
      }
      context.stroke();
    }

    function tick(time: number) {
      elapsed = Math.min(duration, elapsed + (previousTime ? time - previousTime : 0));
      previousTime = time;
      if (time - lastDraw >= 1000 / 30 || elapsed === duration) {
        draw();
        lastDraw = time;
      }
      frame = elapsed < duration ? requestAnimationFrame(tick) : 0;
    }

    function updatePlayback() {
      cancelAnimationFrame(frame);
      frame = 0;
      previousTime = 0;
      if (motionPreference.matches) elapsed = duration;
      draw();
      if (!motionPreference.matches && !document.hidden && visible && elapsed < duration) {
        frame = requestAnimationFrame(tick);
      }
    }

    function resize() {
      if (!canvas || !context) return;
      const bounds = canvas.getBoundingClientRect();
      width = bounds.width;
      height = bounds.height;
      const pixelRatio = Math.min(window.devicePixelRatio || 1, 2);
      canvas.width = Math.round(width * pixelRatio);
      canvas.height = Math.round(height * pixelRatio);
      context.setTransform(pixelRatio, 0, 0, pixelRatio, 0, 0);
      color = getComputedStyle(canvas).color;
      draw();
    }

    const resizeObserver = new ResizeObserver(resize);
    const visibilityObserver = new IntersectionObserver(([entry]) => {
      visible = entry.isIntersecting;
      updatePlayback();
    });
    resize();
    updatePlayback();
    resizeObserver.observe(canvas);
    visibilityObserver.observe(canvas);
    motionPreference.addEventListener('change', updatePlayback);
    document.addEventListener('visibilitychange', updatePlayback);

    return () => {
      cancelAnimationFrame(frame);
      resizeObserver.disconnect();
      visibilityObserver.disconnect();
      motionPreference.removeEventListener('change', updatePlayback);
      document.removeEventListener('visibilitychange', updatePlayback);
    };
  }, []);

  return <canvas ref={canvasRef} className="login-waves" aria-hidden="true" />;
}
