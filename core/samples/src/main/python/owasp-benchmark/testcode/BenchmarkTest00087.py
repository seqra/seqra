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

from flask import redirect, url_for, request, make_response, render_template
from helpers.utils import escape_for_html

def init(app):

	@app.route('/benchmark/pathtraver-00/BenchmarkTest00087', methods=['GET'])
	def BenchmarkTest00087_get():
		return BenchmarkTest00087_post()

	@app.route('/benchmark/pathtraver-00/BenchmarkTest00087', methods=['POST'])
	def BenchmarkTest00087_post():
		RESPONSE = ""

		param = request.form.get("BenchmarkTest00087")
		if not param:
			param = ""

		import configparser
		
		bar = 'safe!'
		conf48394 = configparser.ConfigParser()
		conf48394.add_section('section48394')
		conf48394.set('section48394', 'keyA-48394', 'a-Value')
		conf48394.set('section48394', 'keyB-48394', param)
		bar = conf48394.get('section48394', 'keyB-48394')

		import os
		import helpers.utils

		fileName = f'{helpers.utils.TESTFILES_DIR}/{bar}'
		if os.path.exists(fileName):
			RESPONSE += ( f"File \'{escape_for_html(fileName)}\' exists." )
		else:
			RESPONSE += ( f"File \'{escape_for_html(fileName)}\' does not exist." )

		return RESPONSE

