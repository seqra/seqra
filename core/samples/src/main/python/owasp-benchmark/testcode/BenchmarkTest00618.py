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

	@app.route('/benchmark/pathtraver-01/BenchmarkTest00618', methods=['GET'])
	def BenchmarkTest00618_get():
		return BenchmarkTest00618_post()

	@app.route('/benchmark/pathtraver-01/BenchmarkTest00618', methods=['POST'])
	def BenchmarkTest00618_post():
		RESPONSE = ""

		import helpers.utils
		param = ""
		
		for name in request.headers.keys():
			if name.lower() in helpers.utils.commonHeaderNames:
				continue
		
			if request.headers.get_all(name):
				param = name
				break

		import configparser
		
		bar = 'safe!'
		conf42704 = configparser.ConfigParser()
		conf42704.add_section('section42704')
		conf42704.set('section42704', 'keyA-42704', 'a-Value')
		conf42704.set('section42704', 'keyB-42704', param)
		bar = conf42704.get('section42704', 'keyB-42704')

		import helpers.utils

		fileName = None
		fd = None

		try:
			fileName = f'{helpers.utils.TESTFILES_DIR}/{bar}'
			with open(fileName, 'rb') as fd:
				RESPONSE += (
					f'The beginning of file: \'{escape_for_html(fileName)}\' is:\n\n'
					f'{escape_for_html(fd.read(1000).decode('utf-8'))}'
				)
		except IOError as e:
			RESPONSE += (
				f'Problem reading from file \'{{escape_for_html(fileName)}}\': '
				f'{escape_for_html(e.strerror)}'
			)

		return RESPONSE

