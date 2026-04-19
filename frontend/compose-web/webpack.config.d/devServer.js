// webpack dev server proxy — /api 요청을 log-generator 백엔드로 전달
if (config.devServer) {
    config.devServer.proxy = [
        {
            context: ['/api'],
            target: 'http://localhost:28090',
            changeOrigin: true
        }
    ];
}
