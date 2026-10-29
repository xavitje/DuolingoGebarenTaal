import { render } from 'preact';
import './styles/app.css';
import { App } from './App';
import { initAuth } from './lib/auth';

void initAuth();
render(<App />, document.getElementById('app')!);
