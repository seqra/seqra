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

	@app.route('/benchmark/pathtraver-01/BenchmarkTest00745', methods=['GET'])
	def BenchmarkTest00745_get():
		return BenchmarkTest00745_post()

	@app.route('/benchmark/pathtraver-01/BenchmarkTest00745', methods=['POST'])
	def BenchmarkTest00745_post():
		RESPONSE = ""

		values = request.args.getlist("BenchmarkTest00745")
		param = ""
		if values:
			param = values[0]

		import configparser
		
		bar = 'safe!'
		conf97281 = configparser.ConfigParser()
		conf97281.add_section('section97281')
		conf97281.set('section97281', 'keyA-97281', 'a_Value')
		conf97281.set('section97281', 'keyB-97281', param)
		bar = conf97281.get('section97281', 'keyA-97281')

		import pathlib
		import helpers.utils

		try:
			testfiles = pathlib.Path(helpers.utils.TESTFILES_DIR)
			p = testfiles / bar
			RESPONSE += (
				f'The beginning of file: \'{escape_for_html(str(p))}\' is:\n\n'
				f'{escape_for_html(p.read_text()[:1000])}'
			)
		except OSError:
			RESPONSE += (
				f'Problem reading from file \'{{escape_for_html(fileName)}}\': '
				f'{escape_for_html(e.strerror)}'
			)

		return RESPONSE

