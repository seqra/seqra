'''
OWASP Benchmark for Python v0.1

This file is part of the Open Web Application Security Project (OWASP) Benchmark Project.
For details, please see https://owasp.org/www-project-benchmark.

The OWASP Benchmark is free software: you can redistribute it and/or modify it under the terms
of the GNU General Public License as published by the Free Software Foundation, version 3.

The OWASP Benchmark is distributed in the hope that it will be useful, but WITHOUT ANY
WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
PURPOSE. See the GNU General Public License for more details.

  Author: Theo Cartsonis
  Created: 2025
'''

import os
import importlib

from flask import (Flask, redirect, request, render_template, send_from_directory)

app = Flask(__name__)

app.secret_key = b'if you need this you are doing something wrong'

test_files = [f'testcode.{file}' for file in filter(lambda str: str.startswith('Benchmark'), os.listdir('testcode'))]

for test in test_files:
	if not test.endswith('__.py') and test.endswith('.py'):
		testmod = importlib.import_module(test[:-len('.py')])
		testmod.init(app)



@app.route('/benchmark/<path:mypath>')
def show_page(mypath: str):
	if mypath.endswith(".html") and not mypath.endswith("404.html"):
		return render_template(f"web/{mypath}")
	else:
		return send_from_directory(os.path.join(app.root_path, 'templates'), mypath)

@app.route('/')
def default_page():
	return redirect("benchmark/Index.html")

@app.route('/redirected')
def redirected():
	return 'you\'ve been pwned'

if __name__ == '__main__':
	app.run()
