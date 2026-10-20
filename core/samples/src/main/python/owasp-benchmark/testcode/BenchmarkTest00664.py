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

	@app.route('/benchmark/pathtraver-01/BenchmarkTest00664', methods=['GET'])
	def BenchmarkTest00664_get():
		return BenchmarkTest00664_post()

	@app.route('/benchmark/pathtraver-01/BenchmarkTest00664', methods=['POST'])
	def BenchmarkTest00664_post():
		RESPONSE = ""

		param = request.args.get("BenchmarkTest00664")
		if not param:
			param = ""

		import configparser
		
		bar = 'safe!'
		conf55264 = configparser.ConfigParser()
		conf55264.add_section('section55264')
		conf55264.set('section55264', 'keyA-55264', 'a_Value')
		conf55264.set('section55264', 'keyB-55264', param)
		bar = conf55264.get('section55264', 'keyA-55264')

		import pathlib
		import helpers.utils

		try:
			testfiles = pathlib.Path(helpers.utils.TESTFILES_DIR)
			p = (testfiles / bar).resolve()

			if not str(p).startswith(str(testfiles)):
				RESPONSE += (
					"Invalid Path."
				)
				return RESPONSE

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

